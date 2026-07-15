globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import StableMessager from './StableMessager';

let container = null;
let root = null;
let messager = null;

const renderMessager = () => {
  act(() => {
    root.render(<StableMessager ref={ref => { messager = ref; }} />);
  });
};

beforeEach(() => {
  container = document.createElement('div');
  document.body.appendChild(container);
  root = createRoot(container);
});

afterEach(() => {
  if (root) {
    act(() => {
      root.unmount();
    });
  }
  container.remove();
  container = null;
  root = null;
  messager = null;
});

describe('StableMessager', () => {
  test('closes alert and invokes result when Ok is clicked', () => {
    const result = vi.fn();
    renderMessager();

    act(() => {
      messager.alert({
        title: 'Session Expired!',
        icon: 'info',
        msg: 'Your session expired',
        result,
      });
    });

    expect(container.textContent).toContain('Session Expired!');
    expect(container.textContent).toContain('Your session expired');

    act(() => {
      container.querySelector('.cpay-stable-messager-action').dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(container.textContent).not.toContain('Session Expired!');
    expect(result).toHaveBeenCalledWith(true);
  });

  test('returns false when Cancel is clicked on a confirmation dialog', () => {
    const result = vi.fn();
    renderMessager();

    act(() => {
      messager.confirm({
        title: 'Confirm Logout',
        msg: 'Are you sure?',
        result,
      });
    });

    const buttons = Array.from(container.querySelectorAll('.cpay-stable-messager-action'));
    expect(buttons.map(button => button.textContent)).toEqual(['Ok', 'Cancel']);

    act(() => {
      buttons[1].dispatchEvent(new MouseEvent('click', { bubbles: true }));
    });

    expect(result).toHaveBeenCalledWith(false);
    expect(container.textContent).not.toContain('Confirm Logout');
  });
});
