import React, { useState } from 'react';
import { Card, Segmented } from '../../../ui';
import MerchantWebhookEndpointsPanel from './MerchantWebhookEndpointsPanel';
import MerchantWebhookDeliveriesPanel from './MerchantWebhookDeliveriesPanel';

interface MerchantModuleWebhooksProps {
  sessionExpired?: () => void;
}

const TABS = [
  { key: 'endpoints', label: 'Endpoints' },
  { key: 'deliveries', label: 'Deliveries' },
];

/**
 * Audit N6: merchant webhook manager. Exercises the session-scoped
 * `/api/v2/merchant-self-service/webhooks` endpoints exposed by
 * `MerchantSelfServiceController` — register/update endpoints, rotate signing
 * secrets, inspect the delivery log, and replay failed deliveries.
 */
function MerchantModuleWebhooks({
  sessionExpired,
}: MerchantModuleWebhooksProps): React.ReactElement {
  const [active, setActive] = useState('endpoints');

  return (
    <Card flush>
      <div style={{ padding: 'var(--ios-space-4)' }}>
        <Segmented items={TABS} active={active} onChange={setActive} />
      </div>
      {active === 'deliveries' ? (
        <MerchantWebhookDeliveriesPanel sessionExpired={sessionExpired} />
      ) : (
        <MerchantWebhookEndpointsPanel sessionExpired={sessionExpired} />
      )}
    </Card>
  );
}

export default MerchantModuleWebhooks;
