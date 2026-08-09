import React from 'react';
import { NavGroup, NavItem, Icons } from '../ui';

const navGroups = [
  {
    title: 'Workspace',
    items: [
      { value: 'dashboard', text: 'Dashboard', Icon: Icons.DashboardIcon },
      { value: 'channels', text: 'Payment Channels', Icon: Icons.CardsIcon },
      { value: 'statement', text: 'Statement', Icon: Icons.ReceiptIcon },
    ],
  },
  {
    title: 'Operations',
    items: [
      { value: 'payments', text: 'Payments', Icon: Icons.PaymentsIcon },
      { value: 'vending', text: 'Vending', Icon: Icons.StoreIcon },
      { value: 'webhooks', text: 'Webhooks', Icon: Icons.LightningIcon },
      { value: 'sms', text: 'SMS', Icon: Icons.SmsIcon },
      { value: 'transactions', text: 'Transactions', Icon: Icons.ReceiptIcon },
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

/** Merchant portal navigation. */
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
