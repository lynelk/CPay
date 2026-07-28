import React from 'react';

const iconProps = {
  viewBox: '0 0 24 24',
  fill: 'none',
  xmlns: 'http://www.w3.org/2000/svg',
  'aria-hidden': 'true',
  focusable: 'false'
};

export const MenuIcon = () => (
  <svg {...iconProps}>
    <path d="M5 7h14M5 12h14M5 17h14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
  </svg>
);

export const CalendarIcon = () => (
  <svg {...iconProps}>
    <rect x="5" y="5" width="14" height="14" rx="3" stroke="currentColor" strokeWidth="2" />
    <path d="M8 3v4M16 3v4M5 10h14" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
  </svg>
);

export const MailIcon = () => (
  <svg {...iconProps}>
    <rect x="4" y="6" width="16" height="12" rx="3" stroke="currentColor" strokeWidth="2" />
    <path d="m6 8 6 5 6-5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export const PaymentsIcon = () => (
  <svg {...iconProps}>
    <path d="M12 3v18M16 7.5c-.7-.9-2-1.5-3.6-1.5-2 0-3.4 1-3.4 2.4 0 1.6 1.8 2.2 3.7 2.7 2 .6 3.8 1.2 3.8 3.2 0 1.5-1.5 2.7-3.8 2.7-1.9 0-3.5-.7-4.4-1.9" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export const CardsIcon = () => (
  <svg {...iconProps}>
    <rect x="4" y="4" width="7" height="7" rx="2" stroke="currentColor" strokeWidth="2" />
    <rect x="13" y="4" width="7" height="7" rx="2" stroke="currentColor" strokeWidth="2" />
    <rect x="4" y="13" width="7" height="7" rx="2" stroke="currentColor" strokeWidth="2" />
    <path d="M16.5 14v5M14 16.5h5" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />
  </svg>
);

export const CheckIcon = () => (
  <svg {...iconProps}>
    <path d="m5 12.5 4.2 4.2L19 7" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export const CloseIcon = () => (
  <svg {...iconProps}>
    <path d="m7 7 10 10M17 7 7 17" stroke="currentColor" strokeWidth="2.2" strokeLinecap="round" />
  </svg>
);
