import React from 'react';
import { NavGroup, NavItem, Icons } from '../ui';

const navGroups = [
  {
    title: 'Home',
    items: [{ value: 'dashboard', text: 'Dashboard', Icon: Icons.DashboardIcon, capability: 'HOME' }],
  },
  {
    title: 'Payments & Transactions',
    items: [
      { value: 'payments', text: 'Payments', Icon: Icons.PaymentsIcon, capability: 'PAYMENTS_TRANSACTIONS' },
      { value: 'transactions', text: 'Transactions', Icon: Icons.ReceiptIcon, capability: 'PAYMENTS_TRANSACTIONS' },
      { value: 'statement', text: 'Statements & Balances', Icon: Icons.ReceiptIcon, capability: 'PAYMENTS_TRANSACTIONS' },
    ],
  },
  {
    title: 'KYC & Customer Mgt',
    items: [{ value: 'kyc', text: 'Business Verification', Icon: Icons.ShieldIcon, capability: 'KYC_CUSTOMER_MGT' }],
  },
  {
    title: 'Billing',
    items: [{ value: 'billing', text: 'Usage & Pricing', Icon: Icons.CardsIcon, capability: 'BILLING' }],
  },
  {
    title: 'Communication',
    items: [{ value: 'sms', text: 'SMS · WhatsApp · USSD', Icon: Icons.SmsIcon, capability: 'COMMUNICATION' }],
  },
  {
    title: 'Developers & Integrations',
    items: [
      { value: 'channels', text: 'Payment Channels', Icon: Icons.CardsIcon, capability: 'DEVELOPERS_INTEGRATIONS' },
      { value: 'webhooks', text: 'Webhooks', Icon: Icons.LightningIcon, capability: 'DEVELOPERS_INTEGRATIONS' },
    ],
  },
  {
    title: 'Administration',
    items: [
      { value: 'admins', text: 'Team & Users', Icon: Icons.UsersIcon, capability: 'ADMINISTRATION' },
      { value: 'audittrail', text: 'Audit Trail', Icon: Icons.HistoryIcon, capability: 'AUDIT' },
      { value: 'settings', text: 'Settings', Icon: Icons.SettingsIcon, capability: 'ADMINISTRATION' },
    ],
  },
];

/** Merchant navigation is rendered from the capabilities of the one authenticated session. */
export default function MainMenuMerchant({ activeItem, onChangeMenu, capabilities = [] }) {
  const allowed = new Set(capabilities);
  return (
    <>
      {navGroups.map((group) => {
        const items = group.items.filter((item) => allowed.has(item.capability));
        if (items.length === 0) return null;
        return (
          <NavGroup title={group.title} key={group.title}>
            {items.map((item) => (
              <NavItem
                key={item.value}
                icon={<item.Icon size={20} />}
                active={activeItem === item.value}
                onClick={() => onChangeMenu(item.value)}
              >
                {item.text}
              </NavItem>
            ))}
          </NavGroup>
        );
      })}
      <NavGroup bottom>
        <NavItem danger icon={<Icons.LogoutIcon size={20} />} onClick={() => onChangeMenu('exit')}>
          Logout
        </NavItem>
      </NavGroup>
    </>
  );
}
