import React, { useEffect, useMemo, useState } from 'react';

type EndpointState<T> = {
  loading: boolean;
  error: string | null;
  data: T | null;
};

type DashboardSection = {
  title: string;
  description: string;
  endpoint: string;
  owner: 'Merchant' | 'Finance' | 'Compliance' | 'Operations' | 'Developer';
};

const sections: DashboardSection[] = [
  {
    title: 'Merchant onboarding',
    description: 'Tracks onboarding steps, KYB blockers, channel setup, sandbox readiness and go-live checklist status.',
    endpoint: '/api/v2/product/onboarding/progress',
    owner: 'Merchant',
  },
  {
    title: 'Developer portal',
    description: 'Surfaces developer apps, API key metadata, sandbox guides, channel journeys and go-live guidance.',
    endpoint: '/api/v2/product/developer/apps',
    owner: 'Developer',
  },
  {
    title: 'Payment links and checkout',
    description: 'Reviews payment link lifecycle, checkout sessions, invoice records and customer payment journeys.',
    endpoint: '/api/v2/product/payment-links',
    owner: 'Merchant',
  },
  {
    title: 'Finance operations',
    description: 'Reviews settlement batches, reconciliation exceptions, treasury positions, daily close and report exports.',
    endpoint: '/api/v2/finance/settlement-batches',
    owner: 'Finance',
  },
  {
    title: 'Compliance operations',
    description: 'Reviews KYB/KYC profiles, beneficial ownership, compliance cases, screening results and evidence exports.',
    endpoint: '/api/v2/compliance/cases',
    owner: 'Compliance',
  },
  {
    title: 'Cross-border readiness',
    description: 'Reviews corridors, beneficiaries, FX quotes, transfer lifecycle, treasury exposure and corridor settlement.',
    endpoint: '/api/v2/cross-border/corridors',
    owner: 'Operations',
  },
];

function useEndpoint<T>(endpoint: string): EndpointState<T> {
  const [state, setState] = useState<EndpointState<T>>({ loading: false, error: null, data: null });

  useEffect(() => {
    const controller = new AbortController();
    setState({ loading: true, error: null, data: null });

    fetch(endpoint, {
      credentials: 'include',
      headers: { Accept: 'application/json' },
      signal: controller.signal,
    })
      .then((response) => {
        if (!response.ok) {
          throw new Error(`${response.status} ${response.statusText}`);
        }
        return response.json() as Promise<T>;
      })
      .then((data) => setState({ loading: false, error: null, data }))
      .catch((error: Error) => {
        if (controller.signal.aborted) return;
        setState({ loading: false, error: error.message, data: null });
      });

    return () => controller.abort();
  }, [endpoint]);

  return state;
}

function StatusBadge({ state }: { state: EndpointState<unknown> }): React.ReactElement {
  if (state.loading) {
    return <span className="pm-badge pm-badge-muted">Checking</span>;
  }
  if (state.error) {
    return <span className="pm-badge pm-badge-warn">Needs wiring</span>;
  }
  return <span className="pm-badge pm-badge-ok">Reachable</span>;
}

function SectionCard({ section }: { section: DashboardSection }): React.ReactElement {
  const state = useEndpoint<unknown>(section.endpoint);
  const detail = useMemo(() => {
    if (state.loading) return 'Loading endpoint status…';
    if (state.error) return `Endpoint did not return usable JSON yet: ${state.error}`;
    if (Array.isArray(state.data)) return `${state.data.length} records returned.`;
    if (state.data && typeof state.data === 'object') return 'Endpoint returned a workflow payload.';
    return 'Endpoint returned no payload.';
  }, [state]);

  return (
    <article className="pm-card">
      <div className="pm-card-header">
        <div>
          <p className="pm-owner">{section.owner}</p>
          <h2>{section.title}</h2>
        </div>
        <StatusBadge state={state} />
      </div>
      <p>{section.description}</p>
      <code>{section.endpoint}</code>
      <p className="pm-detail">{detail}</p>
    </article>
  );
}

export default function ProductionMaturityDashboard(): React.ReactElement {
  return (
    <main className="pm-shell">
      <style>{`
        .pm-shell {
          padding: 32px;
          color: #172033;
          background: #f7f9fc;
          min-height: 100vh;
        }
        .pm-hero {
          max-width: 1120px;
          margin: 0 auto 24px auto;
        }
        .pm-hero h1 {
          margin: 0 0 8px 0;
          font-size: 2rem;
        }
        .pm-hero p {
          max-width: 820px;
          color: #526071;
          line-height: 1.55;
        }
        .pm-grid {
          max-width: 1120px;
          margin: 0 auto;
          display: grid;
          grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
          gap: 16px;
        }
        .pm-card {
          background: #fff;
          border: 1px solid #d9e2ef;
          border-radius: 16px;
          padding: 18px;
          box-shadow: 0 10px 30px rgba(15, 23, 42, 0.06);
        }
        .pm-card-header {
          display: flex;
          align-items: flex-start;
          justify-content: space-between;
          gap: 12px;
        }
        .pm-card h2 {
          margin: 0 0 8px 0;
          font-size: 1.125rem;
        }
        .pm-card p {
          color: #526071;
          line-height: 1.5;
        }
        .pm-card code {
          display: block;
          padding: 10px;
          border-radius: 10px;
          background: #f2f5f9;
          color: #2d3b4f;
          overflow-wrap: anywhere;
        }
        .pm-owner {
          margin: 0 0 4px 0;
          font-size: 0.75rem;
          text-transform: uppercase;
          letter-spacing: 0.08em;
          color: #6b7788;
        }
        .pm-badge {
          border-radius: 999px;
          padding: 6px 10px;
          font-size: 0.75rem;
          white-space: nowrap;
        }
        .pm-badge-ok { background: #e8f7ed; color: #176b36; }
        .pm-badge-warn { background: #fff3df; color: #8a5200; }
        .pm-badge-muted { background: #eef2f7; color: #536274; }
        .pm-detail { font-size: 0.875rem; }
      `}</style>
      <section className="pm-hero">
        <h1>Production maturity dashboard</h1>
        <p>
          This dashboard wires the new finance, compliance, cross-border and product-experience endpoint
          surfaces into the portal shell. It is intentionally status-driven so teams can see which workflow
          APIs are live, which need routing, and which still require backend orchestration or UI-specific follow-up.
        </p>
      </section>
      <section className="pm-grid" aria-label="Production maturity workflow endpoint status">
        {sections.map((section) => (
          <SectionCard key={section.endpoint} section={section} />
        ))}
      </section>
    </main>
  );
}
