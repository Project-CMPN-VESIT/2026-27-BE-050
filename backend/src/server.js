const express = require('express');
const cors = require('cors');
const { config } = require('./config');
const {
  getLiveEvents,
  getEventById,
  getEvidenceById,
  getNearbyEvents,
  getEvidenceNearby,
  getAlerts,
  getSourceStatus,
  getAlertSummary,
} = require('./services/disasterService');

const app = express();
app.use(cors());
app.use(express.json());

app.get('/health', (req, res) => {
  res.json({
    ok: true,
    app: config.appName,
    status: 'healthy',
    timestamp: Date.now(),
  });
});

app.get('/live-events', async (req, res) => {
  try {
    const events = await getLiveEvents();
    res.json(events);
  } catch (error) {
    res.status(500).json({ error: error.message || 'Failed to fetch live events' });
  }
});

app.get('/live-events/nearby', async (req, res) => {
  try {
    const lat = Number(req.query.lat);
    const lng = Number(req.query.lng);
    const radiusKm = Number(req.query.radiusKm || 250);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      return res.status(400).json({ error: 'lat and lng query params are required' });
    }
    const events = await getNearbyEvents(lat, lng, radiusKm);
    res.json(events);
  } catch (error) {
    res.status(500).json({ error: error.message || 'Failed to fetch nearby events' });
  }
});

app.get('/live-events/:event_id', async (req, res) => {
  try {
    const event = await getEventById(req.params.event_id);
    if (!event) {
      return res.status(404).json({ error: 'Event not found' });
    }
    res.json(event);
  } catch (error) {
    res.status(500).json({ error: error.message || 'Failed to fetch event' });
  }
});

app.get('/evidence/nearby', async (req, res) => {
  try {
    const lat = Number(req.query.lat);
    const lng = Number(req.query.lng);
    const radiusKm = Number(req.query.radiusKm || 250);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      return res.status(400).json({ error: 'lat and lng query params are required' });
    }
    const evidence = await getEvidenceNearby(lat, lng, radiusKm);
    res.json(evidence);
  } catch (error) {
    res.status(500).json({ error: error.message || 'Failed to fetch nearby evidence' });
  }
});

app.get('/evidence/:event_id', async (req, res) => {
  try {
    const evidence = await getEvidenceById(req.params.event_id);
    res.json(evidence);
  } catch (error) {
    res.status(500).json({ error: error.message || 'Failed to fetch evidence' });
  }
});

app.get('/alerts', async (req, res) => {
  try {
    const alerts = await getAlerts();
    res.json(alerts);
  } catch (error) {
    res.status(500).json({ error: error.message || 'Failed to fetch alerts' });
  }
});

app.get('/alerts/user-location', async (req, res) => {
  try {
    const lat = Number(req.query.lat);
    const lng = Number(req.query.lng);
    const radiusKm = Number(req.query.radiusKm || 250);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      return res.status(400).json({ error: 'lat and lng query params are required' });
    }
    const events = await getNearbyEvents(lat, lng, radiusKm);
    const summary = await getAlertSummary();
    res.json({
      location: { latitude: lat, longitude: lng },
      nearbyEvents: events,
      summary,
    });
  } catch (error) {
    res.status(500).json({ error: error.message || 'Failed to fetch user-local alerts' });
  }
});

app.get('/disasters/global', async (req, res) => {
  try {
    const events = await getLiveEvents();
    res.json(events);
  } catch (error) {
    res.status(500).json({ error: error.message || 'Failed to get global disasters' });
  }
});

app.get('/disasters/india', async (req, res) => {
  try {
    const events = await getLiveEvents();
    const indiaEvents = events.filter((event) => {
      const location = (event.location || '').toLowerCase();
      return location.includes('india') || location.includes('mumbai') || location.includes('delhi') || location.includes('bengaluru') || location.includes('chennai');
    });
    res.json(indiaEvents);
  } catch (error) {
    res.status(500).json({ error: error.message || 'Failed to get India disasters' });
  }
});

app.post('/refresh-events', async (req, res) => {
  try {
    const events = await getLiveEvents();
    res.json({ refreshedAt: Date.now(), count: events.length, events });
  } catch (error) {
    res.status(500).json({ error: error.message || 'Failed to refresh events' });
  }
});

app.get('/sources/status', async (req, res) => {
  try {
    const status = await getSourceStatus();
    res.json(status);
  } catch (error) {
    res.status(500).json({ error: error.message || 'Failed to fetch source status' });
  }
});

app.listen(config.port, '0.0.0.0', () => {
  console.log(`${config.appName} backend listening on http://localhost:${config.port}`);
});

module.exports = app;
