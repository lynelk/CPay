import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

const read = relativePath => fs.readFileSync(path.join(__dirname, relativePath), 'utf8');

describe('dialog CSS interaction safety', () => {
  test('keeps StableMessager actions clickable above the mask', () => {
    const css = read('index.css');

    expect(css).toMatch(/\.cpay-stable-messager-layer\s*\{[^}]*pointer-events:\s*none;/s);
    expect(css).toMatch(/\.cpay-stable-messager-mask\s*\{[^}]*pointer-events:\s*auto;/s);
    expect(css).toMatch(/\.cpay-stable-messager-window\s*\{[^}]*pointer-events:\s*auto;/s);
    expect(css).toMatch(/\.cpay-stable-messager-action\s*\{[^}]*cursor:\s*pointer;/s);
  });

  test('keeps iOS sheets modal, scrollable, and with a pinned footer', () => {
    const css = read('styles/ios-system.css');

    expect(css).toMatch(/\.ios-scrim-layer\s*\{[^}]*position:\s*fixed;[^}]*display:\s*grid;/s);
    expect(css).toMatch(/\.ios-sheet\s*\{[^}]*max-height:\s*calc\(100vh - 64px\);[^}]*display:\s*flex;[^}]*flex-direction:\s*column;/s);
    expect(css).toMatch(/\.ios-sheet__body\s*\{[^}]*overflow-y:\s*auto;[^}]*flex:\s*1;/s);
    expect(css).toMatch(/\.ios-sheet__footer\s*\{[^}]*display:\s*flex;[^}]*justify-content:\s*flex-end;/s);
  });
});
