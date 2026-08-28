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
          <Route path="/signup" element={<CitoSignupGateway />} />
          <Route path="/verify-email" element={<VerifyEmail />} />

          {/* Canonical single-domain back-office surfaces. */}
          <Route path="/bo" element={<CitoAccessGateway />} />
          <Route path="/bo/admin" element={<PlatformLogin />} />
          <Route path="/bo/admin/operations" element={<OperationsConsole />} />
          <Route path="/bo/admin/provider-treasury" element={<ProviderTreasuryConsole />} />
          <Route path="/bo/admin/production-maturity" element={<ProductionMaturityDashboard />} />
          <Route path="/bo/admin/*" element={<Layout />} />

          <Route path="/bo/partner" element={<PartnerLogin />} />
          <Route path="/bo/partner/*" element={<LayoutMerchant />} />

          {/* Backward-compatible aliases. New links and authentication redirects use /bo. */}
          <Route path="/login" element={<Navigate to="/bo" replace />} />
          <Route path="/portal" element={<Navigate to="/bo/admin" replace />} />
          <Route path="/admin" element={<Navigate to="/bo/admin" replace />} />
          <Route path="/admin/operations" element={<Navigate to="/bo/admin/operations" replace />} />
          <Route path="/admin/provider-treasury" element={<Navigate to="/bo/admin/provider-treasury" replace />} />
          <Route path="/admin/production-maturity" element={<Navigate to="/bo/admin/production-maturity" replace />} />
          <Route path="/admin/*" element={<Navigate to="/bo/admin/dashboard" replace />} />
          <Route path="/partner" element={<Navigate to="/bo/partner" replace />} />
          <Route path="/partner/*" element={<Navigate to="/bo/partner/dashboard" replace />} />
          <Route path="/dashboard/*" element={<Navigate to="/bo/admin/dashboard" replace />} />
          <Route path="/dashboardMerchant/*" element={<Navigate to="/bo/partner/dashboard" replace />} />
          <Route path="/operations" element={<Navigate to="/bo/admin/operations" replace />} />
          <Route path="/provider-treasury" element={<Navigate to="/bo/admin/provider-treasury" replace />} />
          <Route path="/production-maturity" element={<Navigate to="/bo/admin/production-maturity" replace />} />
        </Routes>
      </Suspense>
    </BrowserRouter>
  );
}

export default Routers;
