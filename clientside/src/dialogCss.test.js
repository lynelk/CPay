const fs = require('fs');
const path = require('path');

const css = fs.readFileSync(path.join(__dirname, 'index.css'), 'utf8');

describe('dialog CSS interaction safety', () => {
  test('keeps rc-easyui dialog panels clickable when they carry the window-shadow class', () => {
    expect(css).not.toMatch(/\.window-shadow\s*\{[^}]*pointer-events:\s*none\s*!important;/s);
    expect(css).toMatch(/\.window\.window-shadow,[\s\S]*\.messager-window\.window-shadow\s*\{[^}]*pointer-events:\s*auto\s*!important;/s);
  });

  test('keeps rc-easyui dialog footers clickable above scrollable dialog content', () => {
    expect(css).toMatch(/\.window\s+\.dialog-button,[\s\S]*\.messager-button\s*\{[^}]*position:\s*relative\s*!important;[^}]*z-index:\s*2\s*!important;/s);
    expect(css).toMatch(/\.cpay-dialog-scroll-body\s*\{[^}]*overflow-y:\s*auto\s*!important;/s);
  });
});
