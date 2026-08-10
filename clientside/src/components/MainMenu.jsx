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
      { value: 'transactions', text: 'All Transactions', Icon: Icons.ReceiptIcon },
      { value: 'payoutapprovals', text: 'Payout Approvals', Icon: Icons.PaymentsIcon },
      { value: 'payoutcontrols', text: 'Payout Controls', Icon: Icons.CardsIcon },
      { value: 'reconciliation', text: 'Reconciliation', Icon: Icons.ReconcileIcon },
      { value: 'financeclose', text: 'Finance Close', Icon: Icons.CalendarIcon },
      { value: 'settlementclose', text: 'Settlement Close', Icon: Icons.ReconcileIcon },
    ],
  },
  {
    title: 'KYC & Customer Mgt',
    items: [
      { value: 'merchants', text: 'Customer Directory', Icon: Icons.StoreIcon },
      { value: 'kyccustomers', text: 'KYC & Compliance', Icon: Icons.ShieldIcon },
    ],
  },
  {
    title: 'Billing',
    items: [
      { value: 'billing', text: 'Pricing & Rating', Icon: Icons.CardsIcon },
    ],
  },
  {
    title: 'Communication',
    items: [
      { value: 'communicationrouting', text: 'Channels & Routing', Icon: Icons.SmsIcon },
    ],
  },
  {
    title: 'Developers & Integrations',
    items: [
      { value: 'webhookops', text: 'Webhook Operations', Icon: Icons.LightningIcon },
    ],
  },
  {
    title: 'Administration',
    items: [
      { value: 'admins', text: 'Users & Roles', Icon: Icons.UsersIcon },
      { value: 'audittrail', text: 'Audit Trail', Icon: Icons.HistoryIcon },
      { value: 'settings', text: 'Settings', Icon: Icons.SettingsIcon },
    ],
  },
];

/** Admin portal navigation grouped by the preferred CPay product domains. */
export default function MainMenu({ activeItem, onChangeMenu }) {
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
