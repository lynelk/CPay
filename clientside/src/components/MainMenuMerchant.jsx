import React from 'react';
import { NavGroup, NavItem, Icons } from '../ui';

const navGroups = [
  {
    title: 'Home',
    items: [
      { value: 'dashboard', text: 'Dashboard', Icon: Icons.DashboardIcon },
    ],
  },
  {
    title: 'Payments & Transactions',
    items: [
      { value: 'payments', text: 'Payments', Icon: Icons.PaymentsIcon },
      { value: 'transactions', text: 'Transactions', Icon: Icons.ReceiptIcon },
      { value: 'statement', text: 'Statements & Balances', Icon: Icons.ReceiptIcon },
    ],
  },
  {
    title: 'KYC & Customer Mgt',
    items: [
      { value: 'kyc', text: 'Business Verification', Icon: Icons.ShieldIcon },
    ],
  },
  {
    title: 'Billing',
    items: [
      { value: 'billing', text: 'Usage & Pricing', Icon: Icons.CardsIcon },
    ],
  },
  {
    title: 'Communication',
    items: [
      { value: 'sms', text: 'SMS', Icon: Icons.SmsIcon },
    ],
  },
  {
    title: 'Developers & Integrations',
    items: [
      { value: 'channels', text: 'Payment Channels', Icon: Icons.CardsIcon },
      { value: 'webhooks', text: 'Webhooks', Icon: Icons.LightningIcon },
    ],
  },
  {
    title: 'Administration',
    items: [
      { value: 'admins', text: 'Team & Users', Icon: Icons.UsersIcon },
      { value: 'audittrail', text: 'Audit Trail', Icon: Icons.HistoryIcon },
      { value: 'settings', text: 'Settings', Icon: Icons.SettingsIcon },
    ],
  },
];

/** Merchant portal navigation grouped by the preferred CPay product domains. */
export default function MainMenuMerchant({ activeItem, onChangeMenu }) {
  return (
    <>
      {navGroups.map((group) => (
        <NavGroup title={group.title} key={group.title}>
          {group.items.map((item) => (
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
      ))}
      <NavGroup bottom>
        <NavItem danger icon={<Icons.LogoutIcon size={20} />} onClick={() => onChangeMenu('exit')}>
          Logout
        </NavItem>
      </NavGroup>
    </>
  );
}
