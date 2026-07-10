globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';

vi.mock('./components/Login', () => ({ default: function MockLogin() {
  return <div>Admin login</div>;
} }));

vi.mock('./components/LoginMerchant', () => ({ default: function MockLoginMerchant() {
  return <div>Merchant login</div>;
} }));

vi.mock('./components/MerchantSignup', () => ({ default: function MockMerchantSignup() {
  return <div>Merchant signup</div>;
} }));

vi.mock('./components/Layout', () => ({ default: function MockLayout() {
  return <div>Admin layout</div>;
} }));

vi.mock('./components/LayoutMerchant', () => ({ default: function MockLayoutMerchant() {
  return <div>Merchant layout</div>;
} }));

vi.mock('./features/OperationsConsole', () => ({ default: function MockOperationsConsole() {
  return <div>Operations console</div>;
} }));

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
