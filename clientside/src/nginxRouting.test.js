import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const nginx = fs.readFileSync(path.join(__dirname, '../default.conf.template'), 'utf8');

describe('frontend nginx route ownership', () => {
  test('keeps backend health probes while reserving /status for the public SPA', () => {
    expect(nginx).toMatch(/location = \/status\/health\s*\{[\s\S]*proxy_pass http:\/\/cito-backend\/status\/health;/);
    expect(nginx).not.toMatch(/location ~ \^\/\([^)]*\bstatus\b[^)]*\)/);
    expect(nginx).toMatch(/location \/\s*\{\s*try_files \$uri \$uri\/ \/index\.html;/);
  });
});
