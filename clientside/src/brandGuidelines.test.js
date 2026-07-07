const fs = require('fs');
const path = require('path');

const read = relativePath => fs.readFileSync(path.join(__dirname, relativePath), 'utf8');

describe('CPay brand guideline tokens', () => {
  test('defines the approved CPay palette as reusable application tokens', () => {
    const css = read('index.css');

    expect(css).toContain('--cpay-brand-teal: #1198C4;');
    expect(css).toContain('--cpay-brand-gold: #F3B01B;');
    expect(css).toContain('--cpay-brand-navy: #163B5C;');
    expect(css).toContain('--cpay-brand-slate: #667085;');
    expect(css).toContain('--cpay-brand-soft-gray: #F5F7FA;');
    expect(css).toContain('--cpay-brand-white: #FFFFFF;');
  });

  test('uses the approved Montserrat heading and Inter body font stacks', () => {
    const css = read('index.css');

    expect(css).toContain('--cpay-heading-font: Montserrat, Inter, -apple-system');
    expect(css).toContain('--cpay-body-font: Inter, -apple-system');
    expect(css).toMatch(/\.cpay-page-heading h1\s*\{[^}]*font-family:\s*var\(--cpay-heading-font\);/s);
    expect(css).toMatch(/body\s*\{[^}]*font-family:\s*var\(--cpay-body-font\);/s);
  });

  test('maps the active shell theme to CPay brand colors instead of the old blue system', () => {
    const css = read('index.css');

    expect(css).toContain('--cpay-fuse-blue: var(--cpay-brand-teal);');
    expect(css).toContain('--cpay-fuse-blue-dark: #0E7FA5;');
    expect(css).not.toContain('--cpay-fuse-blue: #0a84ff;');
  });

  test('aligns the shared JavaScript theme with the brand guide', () => {
    const theme = read('components/iosTheme.js');

    expect(theme).toContain("blue: '#1198C4'");
    expect(theme).toContain("orange: '#F3B01B'");
    expect(theme).toContain("label: '#163B5C'");
    expect(theme).toContain('Montserrat, Inter');
    expect(theme).not.toContain("'#007AFF'");
  });
});
