import React from 'react';
import { NavGroup, NavItem, Icons } from '../ui';

const navGroups = [
  { title: 'Operate', items: [
    { value: 'home', text: 'Home', Icon: Icons.DashboardIcon },
    { value: 'merchants-accounts', text: 'Merchants & Accounts', Icon: Icons.StoreIcon },
    { value: 'money-operations', text: 'Money Operations', Icon: Icons.PaymentsIcon },
    { value: 'treasury', text: 'Treasury', Icon: Icons.BarChartIcon },
  ] },
  { title: 'Control', items: [
    { value: 'risk-compliance', text: 'Risk & Compliance', Icon: Icons.ShieldIcon },
    { value: 'providers-integrations', text: 'Providers & Integrations', Icon: Icons.LightningIcon },
    { value: 'platform', text: 'Platform', Icon: Icons.CardsIcon },
  ] },
  { title: 'Manage', items: [
    { value: 'administration', text: 'Administration', Icon: Icons.UsersIcon },
    { value: 'engineering', text: 'Engineering / Internal', Icon: Icons.SettingsIcon },
  ] },
];

/** Canonical admin information architecture. Selection is route-driven by the host shell. */
export default function MainMenu({ activeItem, onChangeMenu }) {
  return (
    <>
      {navGroups.map((group) => (
        <NavGroup title={group.title} key={group.title}>
          {group.items.map((item) => (
            <NavItem key={item.value} icon={<item.Icon size={20} />} active={activeItem === item.value} onClick={() => onChangeMenu(item.value)}>
              {item.text}
            </NavItem>
          ))}
        </NavGroup>
      ))}
      <NavGroup bottom>
        <NavItem danger icon={<Icons.LogoutIcon size={20} />} onClick={() => onChangeMenu('exit')}>Logout</NavItem>
      </NavGroup>
    </>
  );
}
