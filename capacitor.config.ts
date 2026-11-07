import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'net.isntall.spyomap',
  appName: 'Spyo Map',
  // Fallback page only; the app loads the live site below.
  webDir: 'www',
  server: {
    url: 'https://spyo-map.isntall.net',
    cleartext: false,
  },
  android: {
    allowMixedContent: false,
  },
};

export default config;
