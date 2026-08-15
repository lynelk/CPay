import React from 'react';
import { NavGroup, NavItem, Icons } from '../ui';

const navGroups = [
  {
    title: 'Workspace',
    items: [
      { value: 'dashboard', text: 'Dashboard', Icon: Icons.DashboardIcon },
      { value: 'vending', text: 'Vending', Icon: Icons.StoreIcon },
      { value: 'merchants', text: 'Merchants', Icon: Icons.StoreIcon },
      { value: 'transactions', text: 'Transactions', Icon: Icons.ReceiptIcon },
      { value: 'reconciliation', text: 'Reconciliation', Icon: Icons.ReconcileIcon },
      { value: 'financeclose', text: 'Finance Close', Icon: Icons.CalendarIcon },
      { value: 'payoutapprovals', text: 'Payout Approvals', Icon: Icons.PaymentsIcon },
      { value: 'payoutcontrols', text: 'Payout Controls', Icon: Icons.CardsIcon },
      { value: 'settlementclose', text: 'Settlement Close', Icon: Icons.ReconcileIcon },
      { value: 'webhookops', text: 'Webhook Ops', Icon: Icons.SmsIcon },
      { value: 'communicationrouting', text: 'Communication Routing', Icon: Icons.SmsIcon },
    ],
  },
  {
    title: 'Oversight',
    items: [
      { value: 'compliance', text: 'Compliance', Icon: Icons.ShieldIcon },
      { value: 'kybreview', text: 'KYB Review', Icon: Icons.UsersIcon },
      { value: 'certification', text: 'Certification', Icon: Icons.CheckIcon },
      { value: 'treasury', text: 'Treasury', Icon: Icons.BarChartIcon },
      { value: 'productionmaturity', text: 'Maturity', Icon: Icons.LightningIcon },
    ],
  },
  {
    title: 'Administration',
    items: [
      { value: 'admins', text: 'Administrators', Icon: Icons.UsersIcon },
      { value: 'audittrail', text: 'Audit Trail', Icon: Icons.HistoryIcon },
      { value: 'settings', text: 'Settings', Icon: Icons.SettingsIcon },
    ],
  },
];

/** Admin portal navigation. Renders iOS nav items; selection is host-driven. */
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
