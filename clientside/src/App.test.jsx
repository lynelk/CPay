globalThis.IS_REACT_ACT_ENVIRONMENT = true;

import React, { act } from 'react';
import { createRoot } from 'react-dom/client';
import App from './App';

vi.mock('./components/CitoLandingPage', () => ({ default: function MockCitoLandingPage() {
  return <div>Cito public gateway</div>;
} }));

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

it('renders the public gateway at the root route', async () => {
  const div = document.createElement('div');
  const root = createRoot(div);

  await act(async () => {
    root.render(<App />);
  });

  expect(div.textContent).toContain('Cito public gateway');

  act(() => {
    root.unmount();
  });
});
