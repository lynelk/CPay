import React, { Suspense, lazy } from 'react';
import { BrowserRouter, Navigate, Routes, Route } from 'react-router-dom';
import AdminSessionGate from './components/AdminSessionGate';

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
const PublicProductPage = lazy(() => import('./components/PublicExperiencePages').then((module) => ({ default: module.PublicProductPage })));
const PublicStatusPage = lazy(() => import('./components/PublicExperiencePages').then((module) => ({ default: module.PublicStatusPage })));
const PublicContactPage = lazy(() => import('./components/PublicExperiencePages').then((module) => ({ default: module.PublicContactPage })));

function RouteFallback(): React.ReactElement {
  return <div style={{ padding: 24 }}>Loading…</div>;
}

function protectAdmin(element: React.ReactElement): React.ReactElement {
  return <AdminSessionGate>{element}</AdminSessionGate>;
}

function Routers(): React.ReactElement {
  return (
    <BrowserRouter>
      <Suspense fallback={<RouteFallback />}>
        <Routes>
          <Route path="/" element={<CitoLandingPage />} />
          <Route path="/payments" element={<PublicProductPage page="payments" />} />
          <Route path="/payouts" element={<PublicProductPage page="payouts" />} />
          <Route path="/billing" element={<PublicProductPage page="billing" />} />
          <Route path="/operations-platform" element={<PublicProductPage page="operations" />} />
          <Route path="/developer-platform" element={<PublicProductPage page="developer-platform" />} />
          <Route path="/about" element={<PublicProductPage page="about" />} />
          <Route path="/security" element={<PublicProductPage page="security" />} />
          <Route path="/status" element={<PublicStatusPage />} />
          <Route path="/contact" element={<PublicContactPage />} />
          <Route path="/signup" element={<CitoSignupGateway />} />
          <Route path="/verify-email" element={<VerifyEmail />} />

          {/* Canonical single-domain back-office surfaces. */}
          <Route path="/bo" element={<CitoAccessGateway />} />
          <Route path="/bo/admin" element={<PlatformLogin />} />
          <Route path="/bo/admin/operations" element={protectAdmin(<OperationsConsole />)} />
          <Route path="/bo/admin/provider-treasury" element={protectAdmin(<ProviderTreasuryConsole />)} />
          <Route path="/bo/admin/production-maturity" element={protectAdmin(<ProductionMaturityDashboard />)} />
          <Route path="/bo/admin/*" element={protectAdmin(<Layout />)} />

          <Route path="/bo/partner" element={<PartnerLogin />} />
          <Route path="/bo/partner/*" element={<LayoutMerchant />} />

          {/* Backward-compatible aliases. Admin aliases now converge on Insights. */}
          <Route path="/login" element={<Navigate to="/bo" replace />} />
          <Route path="/portal" element={<Navigate to="/bo/admin" replace />} />
          <Route path="/admin" element={<Navigate to="/bo/admin" replace />} />
          <Route path="/admin/operations" element={<Navigate to="/bo/admin/operations" replace />} />
          <Route path="/admin/provider-treasury" element={<Navigate to="/bo/admin/provider-treasury" replace />} />
          <Route path="/admin/production-maturity" element={<Navigate to="/bo/admin/production-maturity" replace />} />
          <Route path="/admin/*" element={<Navigate to="/bo/admin/insights" replace />} />
          <Route path="/partner" element={<Navigate to="/bo/partner" replace />} />
          <Route path="/partner/*" element={<Navigate to="/bo/partner/dashboard" replace />} />
          <Route path="/dashboard/*" element={<Navigate to="/bo/admin/insights" replace />} />
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
