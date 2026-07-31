/**
 * CSS-level checks for Table.tsx's small-screen card fallback. Component
 * behavior (rendering, click handling, expander, and the media-query-driven
 * table/card switch) is covered in Table.test.tsx; this file only checks
 * that the supporting `.ios-table-card*` styles in ios-system.css exist and
 * use the shared design tokens. Uses the same file-text-matching approach as
 * components/modules/dashboardLayout.test.js so it doesn't need `@types/node`
 * ambient types (this file is plain JS, so tsc's checkJs:false skips it).
 */
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const read = relativePath => fs.readFileSync(path.join(__dirname, '..', relativePath), 'utf8');

describe('Table.tsx card fallback CSS', () => {
  test('the card container and cards are defined', () => {
    const css = read('styles/ios-system.css');

    expect(css).toMatch(/\.ios-table-cards\s*\{[^}]*display:\s*flex;/);
    expect(css).toContain('.ios-table-card {');
    expect(css).toContain('.ios-table-card__row {');
    expect(css).toContain('.ios-table-card__label {');
    expect(css).toContain('.ios-table-card__value {');
  });

  test('card surfaces use the shared ios design tokens instead of hardcoded colors', () => {
    const css = read('styles/ios-system.css');

    expect(css).toMatch(/\.ios-table-card\s*\{[^}]*background:\s*var\(--ios-card-bg\);/);
    expect(css).toMatch(/\.ios-table-card--selected\s*\{[^}]*background:\s*var\(--ios-row-selected\);/);
  });
});
