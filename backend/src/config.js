const path = require('path');
require('dotenv').config({ path: path.resolve(__dirname, '../.env') });

const config = {
  port: Number(process.env.PORT || 3000),
  appName: process.env.APP_NAME || 'SafeReady',
  mediaStackApiKey: process.env.MEDIASTACK_API_KEY || '',
  xBearerToken: process.env.X_BEARER_TOKEN || '',
  youtubeApiKey: process.env.YOUTUBE_API_KEY || '',
  redditClientId: process.env.REDDIT_CLIENT_ID || '',
  redditClientSecret: process.env.REDDIT_CLIENT_SECRET || '',
  redditUserAgent: process.env.REDDIT_USER_AGENT || '',
  redditRefreshToken: process.env.REDDIT_REFRESH_TOKEN || '',
  imdApiKey: process.env.IMD_API_KEY || '',
  backendBaseUrl: process.env.BACKEND_BASE_URL || 'http://localhost:3000',
};

const sourceStatus = {
  gdacs: { configured: true, label: 'GDACS' },
  usgs: { configured: true, label: 'USGS' },
  mediastack: { configured: !!config.mediaStackApiKey, label: 'Mediastack' },
  x: { configured: !!config.xBearerToken, label: 'X API' },
  youtube: { configured: !!config.youtubeApiKey, label: 'YouTube' },
  reddit: { configured: !!(config.redditClientId && config.redditClientSecret && config.redditUserAgent), label: 'Reddit' },
  imd: { configured: !!config.imdApiKey, label: 'IMD' },
  reliefweb: { configured: true, label: 'ReliefWeb' },
};

module.exports = { config, sourceStatus };
