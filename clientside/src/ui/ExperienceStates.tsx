import React from 'react';
import type { CitoEnvironment, EnvironmentPortal } from '../shared/environment';
import { useEnvironment } from '../shared/environment';
import { Badge, type BadgeTone } from './Badge';
import { Button } from './Button';
import { Card } from './Card';

export function EnvironmentBadge({ environment }: { environment: CitoEnvironment }): React.ReactElement {
  return <Badge tone={environment === 'PRODUCTION' ? 'danger' : 'info'}>{environment}</Badge>;
}

export function EnvironmentSwitcher({ portal }: { portal: EnvironmentPortal }): React.ReactElement {
  const { environment, setEnvironment } = useEnvironment(portal);
  function change(next: CitoEnvironment): void {
    if (next === environment) return;
    if (
      next === 'PRODUCTION'
      && !window.confirm('Switch to PRODUCTION? Actions can affect live customers and funds.')
    ) return;
    setEnvironment(next);
  }
  return (
    <label className="cito-environment-switcher" data-environment={environment} title={`Current environment: ${environment}`}>
      <span className="sr-only">Environment</span>
      <select value={environment} onChange={(event) => change(event.target.value as CitoEnvironment)}>
        <option value="SANDBOX">Sandbox</option>
        <option value="PRODUCTION">Production</option>
      </select>
    </label>
  );
}

export function EmptyState({
  title,
  description,
  action,
}: {
  title: string;
  description: string;
  action?: React.ReactNode;
}): React.ReactElement {
  return <Card className="cito-state"><h2>{title}</h2><p>{description}</p>{action}</Card>;
}

export function ErrorState({ message, retry }: { message: string; retry?: () => void }): React.ReactElement {
  const generic = /internal application error|internal server error|something went wrong/i.test(message);
  const safeMessage = generic
    ? 'Cito could not load this live section. Other areas remain available while the request is retried.'
    : message;
  return (
    <Card className="cito-state cito-state--error" role="alert">
      <h2>Live data is temporarily unavailable</h2>
      <p>{safeMessage} No fallback figures have been substituted.</p>
      {retry ? <Button onClick={retry}>Try again</Button> : null}
    </Card>
  );
}

export function Skeleton({ label = 'Loading live data' }: { label?: string }): React.ReactElement {
  return <div className="cito-skeleton" role="status" aria-label={label}><span /><span /><span /></div>;
}

export function StatusBadge({ status }: { status: string }): React.ReactElement {
  const normalized = status.toUpperCase();
  let tone: BadgeTone = 'neutral';
  if (['ACTIVE', 'LIVE', 'COMPLETED', 'SUCCESSFUL', 'OPERATIONAL', 'RESOLVED', 'APPROVED', 'ALLOW', 'CLOSED', 'REVIEWED'].includes(normalized)) tone = 'success';
  else if (['FAILED', 'CRITICAL', 'SUSPENDED', 'MAJOR_OUTAGE', 'REJECTED', 'BLOCK', 'HIGH'].includes(normalized)) tone = 'danger';
  else if (['PENDING', 'IN_PROGRESS', 'DEGRADED', 'BLOCKED', 'REVIEW_REQUIRED', 'IN_REVIEW', 'OPEN', 'REVIEW'].includes(normalized)) tone = 'warning';
  return <Badge tone={tone}>{normalized.replaceAll('_', ' ')}</Badge>;
}

export interface TimelineItem {
  id?: string | number;
  event: string;
  status?: string;
  occurredAt?: string;
  detail?: string;
}

export function Timeline({ items }: { items: TimelineItem[] }): React.ReactElement {
  if (!items.length) return <EmptyState title="No timeline events" description="Events will appear as processing evidence is recorded." />;
  return (
    <ol className="cito-timeline">
      {items.map((item, index) => (
        <li key={item.id ?? `${item.event}-${index}`}>
          <span aria-hidden="true" />
          <div><strong>{item.event.replaceAll('_', ' ')}</strong>{item.status ? <StatusBadge status={item.status} /> : null}<small>{item.occurredAt || 'Time not recorded'}</small>{item.detail ? <p>{item.detail}</p> : null}</div>
        </li>
      ))}
    </ol>
  );
}

export interface StepperItem {
  code: string;
  name: string;
  status: string;
  guidance?: string;
  responsibleParty?: string;
}

export function Stepper({ steps }: { steps: StepperItem[] }): React.ReactElement {
  return (
    <ol className="cito-stepper">
      {steps.map((step, index) => (
        <li key={step.code} className={`cito-stepper__item cito-stepper__item--${step.status.toLowerCase()}`}>
          <span>{index + 1}</span>
          <div><strong>{step.name}</strong><StatusBadge status={step.status} />{step.guidance ? <p>{step.guidance}</p> : null}{step.responsibleParty ? <small>Owner: {step.responsibleParty}</small> : null}</div>
        </li>
      ))}
    </ol>
  );
}

export function HighRiskConfirmation({
  open,
  title,
  impact,
  confirmLabel,
  onConfirm,
  onCancel,
}: {
  open: boolean;
  title: string;
  impact: string;
  confirmLabel: string;
  onConfirm: () => void;
  onCancel: () => void;
}): React.ReactElement | null {
  const [acknowledged, setAcknowledged] = React.useState(false);
  React.useEffect(() => setAcknowledged(false), [open]);
  if (!open) return null;
  return (
    <div className="cito-confirmation-backdrop" role="presentation">
      <section className="cito-confirmation" role="dialog" aria-modal="true" aria-labelledby="high-risk-title">
        <h2 id="high-risk-title">{title}</h2><p>{impact}</p>
        <label><input type="checkbox" checked={acknowledged} onChange={(event) => setAcknowledged(event.target.checked)} /> I understand this action affects production.</label>
        <div><Button variant="ghost" onClick={onCancel}>Cancel</Button><Button variant="danger" disabled={!acknowledged} onClick={onConfirm}>{confirmLabel}</Button></div>
      </section>
    </div>
  );
}
