globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';

jest.mock('./components/Login', () => function MockLogin() {
  return <div>Admin login</div>;
});

jest.mock('./components/LoginMerchant', () => function MockLoginMerchant() {
  return <div>Merchant login</div>;
});

jest.mock('./components/MerchantSignup', () => function MockMerchantSignup() {
  return <div>Merchant signup</div>;
});

jest.mock('./components/Layout', () => function MockLayout() {
  return <div>Admin layout</div>;
});

jest.mock('./components/LayoutMerchant', () => function MockLayoutMerchant() {
  return <div>Merchant layout</div>;
});

jest.mock('./features/OperationsConsole', () => function MockOperationsConsole() {
  return <div>Operations console</div>;
});

it('renders without crashing', () => {
  const div = document.createElement('div');
  const root = createRoot(div);

  act(() => {
    root.render(<App />);
  });

  expect(div.textContent).toContain('Merchant login');

  act(() => {
    root.unmount();
  });
});