import React from 'react';

/**
 * Inline-SVG icon set (stroke = currentColor) for the iOS design system.
 * Crisp at any DPI and themeable via CSS color — replaces the legacy PNG
 * `.icon-*` sprites used by the old rc-easyui shell.
 */
export interface IconProps extends React.SVGProps<SVGSVGElement> {
  size?: number;
}

function Svg({ size = 20, children, ...rest }: IconProps & { children: React.ReactNode }): React.ReactElement {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      focusable="false"
      {...rest}
    >
      {children}
    </svg>
  );
}

const s = { stroke: 'currentColor', strokeWidth: 2, strokeLinecap: 'round' as const, strokeLinejoin: 'round' as const };

export const MenuIcon = (p: IconProps) => <Svg {...p}><path d="M5 7h14M5 12h14M5 17h14" {...s} /></Svg>;
export const CloseIcon = (p: IconProps) => <Svg {...p}><path d="m7 7 10 10M17 7 7 17" {...s} /></Svg>;
export const CheckIcon = (p: IconProps) => <Svg {...p}><path d="m5 12.5 4.2 4.2L19 7" {...s} strokeWidth={2.2} /></Svg>;
export const SearchIcon = (p: IconProps) => <Svg {...p}><circle cx="11" cy="11" r="6" {...s} /><path d="m20 20-3.2-3.2" {...s} /></Svg>;
export const CalendarIcon = (p: IconProps) => <Svg {...p}><rect x="5" y="5" width="14" height="14" rx="3" {...s} /><path d="M8 3v4M16 3v4M5 10h14" {...s} /></Svg>;
export const MailIcon = (p: IconProps) => <Svg {...p}><rect x="4" y="6" width="16" height="12" rx="3" {...s} /><path d="m6 8 6 5 6-5" {...s} /></Svg>;
export const DashboardIcon = (p: IconProps) => <Svg {...p}><rect x="4" y="4" width="7" height="9" rx="2" {...s} /><rect x="13" y="4" width="7" height="5" rx="2" {...s} /><rect x="13" y="11" width="7" height="9" rx="2" {...s} /><rect x="4" y="15" width="7" height="5" rx="2" {...s} /></Svg>;
export const UsersIcon = (p: IconProps) => <Svg {...p}><circle cx="9" cy="8" r="3" {...s} /><path d="M4 19c0-2.8 2.2-5 5-5s5 2.2 5 5M17 14c1.7.3 3 1.8 3 3.5" {...s} /></Svg>;
export const ReceiptIcon = (p: IconProps) => <Svg {...p}><path d="M6 3h12v18l-3-2-3 2-3-2-3 2V3Z" {...s} /><path d="M9 8h6M9 12h6" {...s} /></Svg>;
export const StoreIcon = (p: IconProps) => <Svg {...p}><path d="M4 9 5 4h14l1 5M5 9v11h14V9M4 9h16" {...s} /></Svg>;
export const HistoryIcon = (p: IconProps) => <Svg {...p}><path d="M4 12a8 8 0 1 0 3-6.2M4 4v3h3" {...s} /><path d="M12 8v4l3 2" {...s} /></Svg>;
export const SettingsIcon = (p: IconProps) => <Svg {...p}><circle cx="12" cy="12" r="3" {...s} /><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1" {...s} /></Svg>;
export const PaymentsIcon = (p: IconProps) => <Svg {...p}><path d="M12 3v18M16 7.5c-.7-.9-2-1.5-3.6-1.5-2 0-3.4 1-3.4 2.4 0 1.6 1.8 2.2 3.7 2.7 2 .6 3.8 1.2 3.8 3.2 0 1.5-1.5 2.7-3.8 2.7-1.9 0-3.5-.7-4.4-1.9" {...s} /></Svg>;
export const CardsIcon = (p: IconProps) => <Svg {...p}><rect x="3" y="6" width="18" height="12" rx="3" {...s} /><path d="M3 10h18" {...s} /></Svg>;
export const SmsIcon = (p: IconProps) => <Svg {...p}><path d="M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H9l-4 3v-3H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2Z" {...s} /></Svg>;
export const LogoutIcon = (p: IconProps) => <Svg {...p}><path d="M15 5H6a2 2 0 0 0-2 2v10a2 2 0 0 0 2 2h9M12 12h9M18 9l3 3-3 3" {...s} /></Svg>;
export const DownloadIcon = (p: IconProps) => <Svg {...p}><path d="M12 4v11m0 0 4-4m-4 4-4-4M5 19h14" {...s} /></Svg>;
export const UploadIcon = (p: IconProps) => <Svg {...p}><path d="M12 20V9m0 0 4 4m-4-4-4 4M5 5h14" {...s} /></Svg>;
export const PlusIcon = (p: IconProps) => <Svg {...p}><path d="M12 5v14M5 12h14" {...s} /></Svg>;
export const RefreshIcon = (p: IconProps) => <Svg {...p}><path d="M20 12a8 8 0 1 1-2.3-5.6M20 4v4h-4" {...s} /></Svg>;
export const ChevronRightIcon = (p: IconProps) => <Svg {...p}><path d="m9 6 6 6-6 6" {...s} /></Svg>;
export const ChevronDownIcon = (p: IconProps) => <Svg {...p}><path d="m6 9 6 6 6-6" {...s} /></Svg>;
export const SunIcon = (p: IconProps) => <Svg {...p}><circle cx="12" cy="12" r="4" {...s} /><path d="M12 2v2M12 20v2M4 12H2M22 12h-2M5 5l1.5 1.5M17.5 17.5 19 19M19 5l-1.5 1.5M6.5 17.5 5 19" {...s} /></Svg>;
export const MoonIcon = (p: IconProps) => <Svg {...p}><path d="M20 14.5A8 8 0 0 1 9.5 4 8 8 0 1 0 20 14.5Z" {...s} /></Svg>;
export const AutoThemeIcon = (p: IconProps) => <Svg {...p}><circle cx="12" cy="12" r="8" {...s} /><path d="M12 4v16" {...s} /><path d="M12 4a8 8 0 0 1 0 16Z" fill="currentColor" stroke="none" /></Svg>;
