const axios = require('axios');
const { config, sourceStatus } = require('../config');

const EVENT_STATUS = {
  ACTIVE_EVENT: 'ACTIVE_EVENT',
  AT_RISK: 'AT_RISK',
  FORECAST_WARNING: 'FORECAST_WARNING',
  PREPAREDNESS: 'PREPAREDNESS',
  RECOVERY: 'RECOVERY',
  HISTORICAL: 'HISTORICAL',
  GENERAL_INFORMATION: 'GENERAL_INFORMATION',
  POLICY: 'POLICY',
  UNVERIFIED_REPORT: 'UNVERIFIED_REPORT',
  IRRELEVANT: 'IRRELEVANT',
};

const DISASTER_TYPE_MAP = [
  ['flash flood', 'FLASH_FLOOD'],
  ['flashflood', 'FLASH_FLOOD'],
  ['flood', 'FLOOD'],
  ['cyclone', 'CYCLONE'],
  ['hurricane', 'HURRICANE'],
  ['tropical storm', 'TROPICAL_STORM'],
  ['earthquake', 'EARTHQUAKE'],
  ['quake', 'EARTHQUAKE'],
  ['tsunami', 'TSUNAMI'],
  ['landslide', 'LANDSLIDE'],
  ['mudslide', 'MUDSLIDE'],
  ['avalanche', 'AVALANCHE'],
  ['wildfire', 'WILDFIRE'],
  ['forest fire', 'WILDFIRE'],
  ['volcano', 'VOLCANIC_ERUPTION'],
  ['volcanic', 'VOLCANIC_ERUPTION'],
  ['tornado', 'TORNADO'],
  ['storm', 'SEVERE_STORM'],
  ['lightning', 'LIGHTNING'],
  ['heatwave', 'HEATWAVE'],
  ['extreme heat', 'EXTREME_HEAT'],
  ['drought', 'DROUGHT'],
  ['cold wave', 'COLD_WAVE'],
  ['storm surge', 'STORM_SURGE'],
  ['coastal flooding', 'COASTAL_FLOODING'],
  ['dam', 'DAM_RESERVOIR_FLOODING'],
  ['reservoir', 'DAM_RESERVOIR_FLOODING'],
  ['extreme rainfall', 'EXTREME_RAINFALL'],
  ['rainfall', 'EXTREME_RAINFALL'],
];

function normalizeDisasterType(rawText = '') {
  const text = String(rawText || '').toLowerCase();
  for (const [keyword, type] of DISASTER_TYPE_MAP) {
    if (text.includes(keyword)) return type;
  }
  return 'OTHER';
}

function inferEventStatus(text = '') {
  const t = String(text || '').toLowerCase();
  if (!t) return EVENT_STATUS.GENERAL_INFORMATION;

  if (/(currently|right now|is affecting|water has entered|destroyed|evacuat|injured|fatalit|power outage|fallen|breaking|landslide|flooding is occurring)/.test(t)) {
    return EVENT_STATUS.ACTIVE_EVENT;
  }
  if (/(warning|warns|alert|forecast|tomorrow|expected|likely|possible|risk of)/.test(t)) {
    return EVENT_STATUS.FORECAST_WARNING;
  }
  if (/(preparedness|prepared|safety measures|evacuation drill|awareness|ready|get ready|be prepared)/.test(t)) {
    return EVENT_STATUS.PREPAREDNESS;
  }
  if (/(recovery|rebuilding|aftermath|restoration|relief)/.test(t)) {
    return EVENT_STATUS.RECOVERY;
  }
  if (/(2005|2014|historical|in 20\d\d|last year|previously)/.test(t)) {
    return EVENT_STATUS.HISTORICAL;
  }
  if (/(policy|govt|government|announcement|budget|plan|committee)/.test(t)) {
    return EVENT_STATUS.POLICY;
  }
  if (/(reported|says|allegedly|unverified|rumour|rumor|social media)/.test(t)) {
    return EVENT_STATUS.UNVERIFIED_REPORT;
  }
  if (/(risk|vulnerable|at risk|monitoring|watch)/.test(t)) {
    return EVENT_STATUS.AT_RISK;
  }
  return EVENT_STATUS.GENERAL_INFORMATION;
}

function safeText(value) {
  return typeof value === 'string' ? value.trim() : '';
}

function safeNumber(value) {
  const num = Number(value);
  return Number.isFinite(num) ? num : null;
}

function formatEventId(prefix, value) {
  return `${prefix}-${String(value || Date.now()).replace(/\s+/g, '-')}`;
}

function haversineKm(lat1, lon1, lat2, lon2) {
  const R = 6371;
  const dLat = (lat2 - lat1) * Math.PI / 180;
  const dLon = (lon2 - lon1) * Math.PI / 180;
  const a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos(lat1 * Math.PI / 180) * Math.cos(lat2 * Math.PI / 180) *
    Math.sin(dLon / 2) * Math.sin(dLon / 2);
  return 2 * R * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
}

function buildEvidenceItem({
  source,
  sourceType,
  title,
  text,
  url,
  timestamp,
  location,
  country,
  state,
  district,
  city,
  latitude,
  longitude,
  disasterType,
  context,
  semanticConfidence,
  sourceTrust,
  usableAsEvidence,
  duplicateGroup,
  eventId,
}) {
  return {
    source,
    sourceType,
    title: title || 'Disaster report',
    text: text || '',
    originalUrl: url || '',
    timestamp: timestamp || Date.now(),
    location: location || 'Global',
    country: country || null,
    state: state || null,
    district: district || null,
    city: city || null,
    latitude: safeNumber(latitude),
    longitude: safeNumber(longitude),
    disasterType: disasterType || normalizeDisasterType(title || text),
    context: context || inferEventStatus(text || title),
    semanticConfidence: Number(semanticConfidence) || 0.5,
    sourceTrust: Number(sourceTrust) || 0.5,
    relevance: 0.5,
    usableAsEvidence: Boolean(usableAsEvidence),
    duplicateGroup: duplicateGroup || null,
    eventId: eventId || null,
  };
}

function mergeEvents(evidenceItems) {
  const groups = new Map();

  for (const item of evidenceItems) {
    const key = `${(item.disasterType || 'OTHER')}::${(item.location || 'Global')}`.toLowerCase();
    if (!groups.has(key)) {
      groups.set(key, {
        eventId: formatEventId('event', key),
        disasterType: item.disasterType || 'OTHER',
        title: item.title || 'Live disaster event',
        alertLevel: 'WARNING_MONITORING',
        eventStatus: item.context || EVENT_STATUS.ACTIVE_EVENT,
        severity: 'monitoring',
        location: item.location || 'Global',
        country: item.country || null,
        state: item.state || null,
        district: item.district || null,
        city: item.city || null,
        latitude: item.latitude || null,
        longitude: item.longitude || null,
        startTime: item.timestamp,
        lastUpdated: item.timestamp,
        sourceSummary: item.source,
        evidenceConfidence: 0.5,
        evidence: [],
      });
    }

    const bucket = groups.get(key);
    bucket.evidence.push(item);
    bucket.lastUpdated = Math.max(bucket.lastUpdated || 0, item.timestamp || Date.now());
    bucket.startTime = Math.min(bucket.startTime || Date.now(), item.timestamp || Date.now());
    const strongest = Math.max(...bucket.evidence.map(e => Number(e.semanticConfidence || 0) + Number(e.sourceTrust || 0)));
    bucket.evidenceConfidence = Math.min(0.99, strongest / 2);
  }

  return [...groups.values()].map((event) => {
    const topStatus = event.evidence.reduce((best, item) => {
      if ((item.context || '').toString() === EVENT_STATUS.ACTIVE_EVENT) return EVENT_STATUS.ACTIVE_EVENT;
      if ((item.context || '').toString() === EVENT_STATUS.FORECAST_WARNING) return EVENT_STATUS.FORECAST_WARNING;
      if ((item.context || '').toString() === EVENT_STATUS.AT_RISK) return EVENT_STATUS.AT_RISK;
      return best;
    }, EVENT_STATUS.GENERAL_INFORMATION);

    const alertLevel =
      topStatus === EVENT_STATUS.ACTIVE_EVENT ? 'AFFECTED' :
      topStatus === EVENT_STATUS.AT_RISK ? 'AT_RISK' :
      topStatus === EVENT_STATUS.FORECAST_WARNING ? 'WARNING_MONITORING' :
      'UNVERIFIED';

    return {
      ...event,
      eventStatus: topStatus,
      alertLevel,
      sourceSummary: [...new Set(event.evidence.map(e => e.source))].join(', '),
      evidence: event.evidence.sort((a, b) => (b.timestamp || 0) - (a.timestamp || 0)),
    };
  }).sort((a, b) => (b.lastUpdated || 0) - (a.lastUpdated || 0));
}

async function requestJson(url, configOverride = {}) {
  const response = await axios.get(url, {
    timeout: 12000,
    headers: { 'Accept': 'application/json', ...(configOverride.headers || {}) },
    ...configOverride,
  });
  return response.data;
}

async function fetchGdacsEvents() {
  try {
    const data = await requestJson('https://www.gdacs.org/gdacsapi/api/events');
    const items = Array.isArray(data) ? data : data.features || [];
    return items.flatMap((entry) => {
      const props = entry.properties || entry;
      const title = safeText(props.title || props.name || props.event || 'GDACS disaster event');
      const text = safeText(props.description || title);
      const disasterType = normalizeDisasterType(title || text);
      const location = safeText(props.country || props.location || props.place || 'Global');
      const lat = safeNumber(entry.latitude || props.latitude || entry.geometry?.coordinates?.[1]);
      const lng = safeNumber(entry.longitude || props.longitude || entry.geometry?.coordinates?.[0]);
      const ts = Date.now();
      return [
        buildEvidenceItem({
          source: 'GDACS',
          sourceType: 'OFFICIAL',
          title,
          text,
          url: props.url || props.source || '',
          timestamp: ts,
          location,
          latitude: lat,
          longitude: lng,
          disasterType,
          context: inferEventStatus(text || title),
          semanticConfidence: 0.91,
          sourceTrust: 0.95,
          usableAsEvidence: true,
          duplicateGroup: `gdacs-${title}`,
          eventId: formatEventId('gdacs', title),
        }),
      ];
    });
  } catch (error) {
    return [];
  }
}

async function fetchUsgsEvents() {
  try {
    const data = await requestJson('https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson');
    const features = Array.isArray(data.features) ? data.features : [];
    return features.map((feature) => {
      const props = feature.properties || {};
      const title = safeText(props.title || 'Earthquake');
      const place = safeText(props.place || 'Global');
      const coords = feature.geometry && Array.isArray(feature.geometry.coordinates) ? feature.geometry.coordinates : [0, 0];
      const latitude = safeNumber(coords[1]);
      const longitude = safeNumber(coords[0]);
      const disasterType = normalizeDisasterType(title);
      return buildEvidenceItem({
        source: 'USGS',
        sourceType: 'OFFICIAL',
        title,
        text: title,
        url: props.url || '',
        timestamp: safeNumber(props.time) || Date.now(),
        location: place,
        latitude,
        longitude,
        disasterType,
        context: inferEventStatus(title),
        semanticConfidence: 0.92,
        sourceTrust: 0.96,
        usableAsEvidence: true,
        duplicateGroup: `usgs-${title}`,
        eventId: formatEventId('usgs', title),
      });
    });
  } catch (error) {
    return [];
  }
}

async function fetchReliefWeb() {
  try {
    const data = await requestJson('https://api.reliefweb.int/v1/disasters?appname=safeready&limit=10');
    const items = Array.isArray(data.data) ? data.data : [];
    return items.map((entry) => {
      const fields = entry.fields || {};
      const title = safeText(fields.name || 'ReliefWeb report');
      const country = safeText((fields.country || [{}])[0]?.name || 'Global');
      const disasterType = normalizeDisasterType(title);
      return buildEvidenceItem({
        source: 'ReliefWeb',
        sourceType: 'RELIEF_WEB',
        title,
        text: fields.description || title,
        url: fields.url || '',
        timestamp: Date.now(),
        location: country,
        country,
        disasterType,
        context: inferEventStatus(title),
        semanticConfidence: 0.82,
        sourceTrust: 0.88,
        usableAsEvidence: true,
        duplicateGroup: `reliefweb-${title}`,
        eventId: formatEventId('reliefweb', title),
      });
    });
  } catch (error) {
    return [];
  }
}

async function fetchMediastack() {
  if (!config.mediaStackApiKey) return [];
  try {
    const data = await requestJson(`http://api.mediastack.com/v1/news?access_key=${encodeURIComponent(config.mediaStackApiKey)}&keywords=${encodeURIComponent('disaster OR flood OR earthquake OR cyclone OR wildfire OR landslide OR storm OR drought OR heatwave OR flooding')}&languages=en&limit=5`);
    const items = Array.isArray(data.data) ? data.data : [];
    return items.map((item) => {
      const title = safeText(item.title || 'Disaster news');
      const text = safeText(item.description || item.title || '');
      const disasterType = normalizeDisasterType(title || text);
      const location = safeText(item.location || item.country || 'Global');
      return buildEvidenceItem({
        source: 'Mediastack',
        sourceType: 'NEWS',
        title,
        text,
        url: item.url || '',
        timestamp: Date.parse(item.published_at || Date.now()) || Date.now(),
        location,
        country: item.country || null,
        disasterType,
        context: inferEventStatus(text || title),
        semanticConfidence: 0.67,
        sourceTrust: 0.7,
        usableAsEvidence: true,
        duplicateGroup: `mediastack-${title}`,
        eventId: formatEventId('mediastack', title),
      });
    });
  } catch (error) {
    return [];
  }
}

async function fetchImdWarnings() {
  if (!config.imdApiKey) return [];
  try {
    const resp = await requestJson(`https://api.imd.gov.in/public/warnings?api_key=${encodeURIComponent(config.imdApiKey)}`);
    const entries = Array.isArray(resp) ? resp : (resp.data || resp.results || []);
    return entries.map((entry) => {
      const title = entry.title || entry.area || 'IMD warning';
      const text = entry.description || entry.message || title;
      const disasterType = normalizeDisasterType(title || text);
      return buildEvidenceItem({
        source: 'IMD',
        sourceType: 'OFFICIAL',
        title,
        text,
        url: entry.url || '',
        timestamp: Date.now(),
        location: entry.area || entry.region || 'India',
        country: 'India',
        disasterType,
        context: inferEventStatus(text || title),
        semanticConfidence: 0.9,
        sourceTrust: 0.95,
        usableAsEvidence: true,
        duplicateGroup: `imd-${title}`,
        eventId: formatEventId('imd', title),
      });
    });
  } catch (error) {
    return [];
  }
}

async function fetchXPosts() {
  if (!config.xBearerToken) return [];
  try {
    const query = 'flood OR earthquake OR cyclone OR landslide OR wildfire -is:retweet';
    const data = await requestJson(`https://api.x.com/2/tweets/search/recent?query=${encodeURIComponent(query)}&max_results=5`, {
      headers: {
        Authorization: `Bearer ${config.xBearerToken}`,
      },
    });
    const tweets = Array.isArray(data.data) ? data.data : [];
    return tweets.map((tweet) => {
      const text = safeText(tweet.text || 'X post');
      const disasterType = normalizeDisasterType(text);
      return buildEvidenceItem({
        source: 'X',
        sourceType: 'SOCIAL',
        title: 'Crowdsourced report',
        text,
        url: tweet.url || `https://x.com/i/web/status/${tweet.id}`,
        timestamp: safeNumber(tweet.created_at) || Date.now(),
        location: 'Crowdsourced',
        disasterType,
        context: inferEventStatus(text),
        semanticConfidence: 0.58,
        sourceTrust: 0.4,
        usableAsEvidence: false,
        duplicateGroup: `x-${tweet.id}`,
        eventId: formatEventId('x', tweet.id),
      });
    });
  } catch (error) {
    return [];
  }
}

async function fetchRedditPosts() {
  if (!(config.redditClientId && config.redditClientSecret && config.redditUserAgent)) return [];
  try {
    const searchUrl = `https://www.reddit.com/search.json?q=${encodeURIComponent('flood OR earthquake OR cyclone OR landslide OR wildfire OR disaster')}&limit=5`;
    const data = await requestJson(searchUrl);
    const items = Array.isArray(data.data?.children) ? data.data.children : [];
    return items.map((item) => {
      const post = item.data || {};
      const text = safeText(post.title || post.selftext || 'Reddit post');
      const disasterType = normalizeDisasterType(text);
      return buildEvidenceItem({
        source: 'Reddit',
        sourceType: 'SOCIAL',
        title: post.title || 'Reddit disaster discussion',
        text,
        url: `https://www.reddit.com${post.permalink || ''}`,
        timestamp: safeNumber(post.created_utc) ? safeNumber(post.created_utc) * 1000 : Date.now(),
        location: 'Crowdsourced',
        disasterType,
        context: inferEventStatus(text),
        semanticConfidence: 0.52,
        sourceTrust: 0.38,
        usableAsEvidence: false,
        duplicateGroup: `reddit-${post.id}`,
        eventId: formatEventId('reddit', post.id),
      });
    });
  } catch (error) {
    return [];
  }
}

async function fetchYouTubeVideos() {
  if (!config.youtubeApiKey) return [];
  try {
    const query = 'disaster flood earthquake cyclone landslide wildfire';
    const data = await requestJson(`https://www.googleapis.com/youtube/v3/search?part=snippet&q=${encodeURIComponent(query)}&key=${encodeURIComponent(config.youtubeApiKey)}&type=video&order=date&maxResults=5`);
    const items = Array.isArray(data.items) ? data.items : [];
    return items.map((item) => {
      const title = safeText(item.snippet?.title || 'Video');
      const text = safeText(item.snippet?.description || title);
      const disasterType = normalizeDisasterType(title || text);
      return buildEvidenceItem({
        source: 'YouTube',
        sourceType: 'VIDEO',
        title,
        text,
        url: `https://www.youtube.com/watch?v=${item.id?.videoId || ''}`,
        timestamp: Date.parse(item.snippet?.publishedAt || Date.now()) || Date.now(),
        location: 'Video report',
        disasterType,
        context: inferEventStatus(text || title),
        semanticConfidence: 0.56,
        sourceTrust: 0.45,
        usableAsEvidence: false,
        duplicateGroup: `youtube-${item.id?.videoId}`,
        eventId: formatEventId('youtube', item.id?.videoId),
      });
    });
  } catch (error) {
    return [];
  }
}

async function fetchAllLiveEvidence() {
  const results = await Promise.allSettled([
    fetchGdacsEvents(),
    fetchUsgsEvents(),
    fetchReliefWeb(),
    fetchMediastack(),
    fetchImdWarnings(),
    fetchXPosts(),
    fetchRedditPosts(),
    fetchYouTubeVideos(),
  ]);

  const merged = [];
  for (const result of results) {
    if (result.status === 'fulfilled') {
      merged.push(...(result.value || []));
    }
  }
  return merged.filter((item) => item && item.originalUrl !== '' || item.title);
}

async function getLiveEvents() {
  const evidence = await fetchAllLiveEvidence();
  const events = mergeEvents(evidence);
  return events.map((event) => ({
    ...event,
    evidence: event.evidence.map((item) => ({
      ...item,
      sourceType: item.sourceType,
    })),
  }));
}

async function getEventById(eventId) {
  const events = await getLiveEvents();
  return events.find((event) => event.eventId === eventId) || null;
}

async function getEvidenceById(eventId) {
  const event = await getEventById(eventId);
  return event ? event.evidence : [];
}

async function getNearbyEvents(lat, lng, radiusKm = 250) {
  const events = await getLiveEvents();
  const latitude = Number(lat);
  const longitude = Number(lng);
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) return [];

  return events.filter((event) => {
    if (event.latitude == null || event.longitude == null) return false;
    return haversineKm(latitude, longitude, Number(event.latitude), Number(event.longitude)) <= radiusKm;
  });
}

async function getEvidenceNearby(lat, lng, radiusKm = 250) {
  const nearby = await getNearbyEvents(lat, lng, radiusKm);
  return nearby.flatMap((event) => event.evidence || []);
}

async function getAlerts() {
  const events = await getLiveEvents();
  return events.map((event) => ({
    eventId: event.eventId,
    disasterType: event.disasterType,
    alertLevel: event.alertLevel,
    eventStatus: event.eventStatus,
    title: event.title,
    location: event.location,
    evidenceConfidence: event.evidenceConfidence,
    lastUpdated: event.lastUpdated,
  }));
}

async function getSourceStatus() {
  return Object.entries(sourceStatus).map(([key, value]) => ({
    source: value.label,
    key,
    configured: Boolean(value.configured),
    status: value.configured ? 'available' : 'not_configured',
  }));
}

async function getAlertSummary(locationLabel = '') {
  const events = await getLiveEvents();
  if (!events.length) {
    return {
      status: 'NO_ACTIVE_EVENT',
      title: 'No active disaster events detected',
      detail: locationLabel ? `No active events detected near ${locationLabel}` : 'No active events detected',
    };
  }

  const top = events[0];
  return {
    status: top.alertLevel,
    title: top.title,
    location: top.location,
    disasterType: top.disasterType,
    evidenceConfidence: top.evidenceConfidence,
    detail: `${top.alertLevel} • ${top.location} • ${top.disasterType}`,
  };
}

module.exports = {
  getLiveEvents,
  getEventById,
  getEvidenceById,
  getNearbyEvents,
  getEvidenceNearby,
  getAlerts,
  getSourceStatus,
  getAlertSummary,
  normalizeDisasterType,
  inferEventStatus,
};
