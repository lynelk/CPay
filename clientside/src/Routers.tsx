import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Navigate, Routes, Route } from 'react-router-dom';

// Public and authenticated surfaces are code-split so each entry point stays focused and light.
const CitoLandingPage = lazy(() => import('./components/CitoLandingPage'));
const CitoAccessGateway = lazy(() => import('./components/CitoAccessGateway'));
const CitoSignupGateway = lazy(() => import('./components/CitoSignupGateway'));
const VerifyEmail = lazy(() => import('./components/VerifyEmail'));
const PlatformLogin = lazy(() => import('./components/Login'));
const PartnerLogin = lazy(() => import('./components/LoginMerchant'));
const Layout = lazy(() => import('./components/Layout'));
const LayoutMerchant = lazy(() => import('./components/LayoutMerchant'));
const OperationsConsole = lazy(() => import('./features/OperationsConsole'));
const ProviderTreasuryConsole = lazy(() => import('./features/ProviderTreasuryConsole'));
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

          {/* Canonical Cito access surfaces. */}
          <Route path="/admin" element={<PlatformLogin />} />
          <Route path="/admin/operations" element={<OperationsConsole />} />
          <Route path="/admin/provider-treasury" element={<ProviderTreasuryConsole />} />
          <Route path="/admin/production-maturity" element={<ProductionMaturityDashboard />} />
          <Route path="/admin/*" element={<Layout />} />

          <Route path="/partner" element={<PartnerLogin />} />
          <Route path="/partner/*" element={<LayoutMerchant />} />

          {/* Backward-compatible aliases. Authentication redirects land on the canonical paths. */}
          <Route path="/portal" element={<Navigate to="/admin" replace />} />
          <Route path="/dashboard/*" element={<Navigate to="/admin/dashboard" replace />} />
          <Route path="/dashboardMerchant/*" element={<Navigate to="/partner/dashboard" replace />} />
          <Route path="/operations" element={<Navigate to="/admin/operations" replace />} />
          <Route path="/provider-treasury" element={<Navigate to="/admin/provider-treasury" replace />} />
          <Route path="/production-maturity" element={<Navigate to="/admin/production-maturity" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default Routers;
