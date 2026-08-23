import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Navigate, Routes, Route } from 'react-router-dom';

// Public and authenticated surfaces are code-split so each entry point stays focused and light.
const CitoLandingPage = lazy(() => import('./components/CitoLandingPage'));
const CitoAccessGateway = lazy(() => import('./components/CitoAccessGateway'));
const CitoSignupGateway = lazy(() => import('./components/CitoSignupGateway'));
const VerifyEmail = lazy(() => import('./components/VerifyEmail'));
const Layout = lazy(() => import('./components/Layout'));
const LayoutMerchant = lazy(() => import('./components/LayoutMerchant'));
const OperationsConsole = lazy(() => import('./features/OperationsConsole'));
const ProductionMaturityDashboard = lazy(() => import('./features/productionMaturity/ProductionMaturityDashboard'));

function RouteFallback(): React.ReactElement {
  return <div style={{ padding: 24 }}>Loading…</div>;
}

function Routers(): React.ReactElement {
  return (
    <BrowserRouter>
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route path="/" element={<CitoLandingPage />} />
          <Route path="/login" element={<CitoAccessGateway />} />
          <Route path="/signup" element={<CitoSignupGateway />} />
          <Route path="/verify-email" element={<VerifyEmail />} />

          <Route path="/portal" element={<Navigate to="/login?realm=platform" replace />} />
          <Route path="/dashboard/*" element={<Layout />} />
          <Route path="/dashboardMerchant/*" element={<LayoutMerchant />} />
          <Route path="/operations" element={<OperationsConsole />} />
          <Route path="/production-maturity" element={<ProductionMaturityDashboard />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default Routers;
