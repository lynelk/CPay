import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import LoginMerchant from './components/LoginMerchant';
import Login from './components/Login';

// Heavy authenticated surfaces are code-split so the login entry stays light.
const MerchantSignup = lazy(() => import('./components/MerchantSignup'));
const VerifyEmail = lazy(() => import('./components/VerifyEmail'));
const Layout = lazy(() => import('./components/Layout'));
const LayoutMerchant = lazy(() => import('./components/LayoutMerchant'));
const OperationsConsole = lazy(() => import('./features/OperationsConsole'));

function RouteFallback(): React.ReactElement {
  return <div style={{ padding: 24 }}>Loading…</div>;
}

function Routers(): React.ReactElement {
  return (
    <BrowserRouter>
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route path="/" element={<LoginMerchant />} />
          <Route path="/signup" element={<MerchantSignup />} />
          <Route path="/verify-email" element={<VerifyEmail />} />

          <Route path="/portal" element={<Login />} />
          <Route path="/dashboard/*" element={<Layout />} />
          <Route path="/dashboardMerchant/*" element={<LayoutMerchant />} />
          <Route path="/operations" element={<OperationsConsole />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default Routers;
