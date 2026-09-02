import { render, screen } from '@testing-library/react';
import axe from 'axe-core';
import { EmptyState, ErrorState, StatusBadge, Stepper, Timeline } from './ExperienceStates';

describe('product experience states', () => {
  it('renders truthful empty and error states without fallback figures', () => {
    render(<><EmptyState title="No records" description="No live records are in scope." /><ErrorState message="The live query failed." /></>);
    expect(screen.getByText('No records')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('No fallback figures have been substituted');
  });

  it('renders status, lifecycle and transaction evidence semantically', () => {
    render(<main><StatusBadge status="IN_PROGRESS" /><Stepper steps={[{ code: 'ACCOUNT_CREATED', name: 'Account created', status: 'COMPLETED', responsibleParty: 'MERCHANT' }]} /><Timeline items={[{ event: 'REQUEST_ACCEPTED', status: 'PENDING', occurredAt: '2026-01-01T00:00:00Z' }]} /></main>);
    expect(screen.getAllByRole('list')).toHaveLength(2);
    expect(screen.getByText('Owner: MERCHANT')).toBeInTheDocument();
  });

  it('has no automated accessibility violations in shared states', async () => {
    const { container } = render(<main><h1>Account state</h1><EmptyState title="No cases" description="Cases will appear here." /><Stepper steps={[{ code: 'VERIFY', name: 'Verify email', status: 'IN_PROGRESS', guidance: 'Open the verification link.' }]} /></main>);
    const result = await axe.run(container, { rules: { 'color-contrast': { enabled: false } } });
    expect(result.violations).toEqual([]);
  });
});
