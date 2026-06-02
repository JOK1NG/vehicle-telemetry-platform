import type { SVGProps } from 'react';

type IconProps = SVGProps<SVGSVGElement>;

const baseProps: SVGProps<SVGSVGElement> = {
  viewBox: '0 0 24 24',
  fill: 'none',
  xmlns: 'http://www.w3.org/2000/svg',
};

export const LogoIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <rect x="2" y="6" width="20" height="13" rx="3" fill="currentColor" opacity=".15" />
    <rect x="2" y="6" width="20" height="13" rx="3" stroke="currentColor" strokeWidth={1.6} />
    <path d="M6 12h12" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
    <circle cx="7" cy="12" r="1.2" fill="currentColor" />
    <circle cx="17" cy="12" r="1.2" fill="currentColor" />
    <path d="M9 19v2M15 19v2" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
  </svg>
);

export const DashboardIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path
      d="M3 13h7V3H3v10Zm0 8h7v-6H3v6Zm11 0h7V11h-7v10Zm0-18v6h7V3h-7Z"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinejoin="round"
    />
  </svg>
);

export const VehiclesIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path
      d="M3 13l1.5-5A2 2 0 0 1 6.4 6.5h11.2A2 2 0 0 1 19.5 8L21 13v5a1 1 0 0 1-1 1h-1a1 1 0 0 1-1-1v-1H6v1a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1v-5Z"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinejoin="round"
    />
    <circle cx="7.5" cy="15.5" r="1.5" fill="currentColor" />
    <circle cx="16.5" cy="15.5" r="1.5" fill="currentColor" />
  </svg>
);

export const SearchIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <circle cx="11" cy="11" r="7" stroke="currentColor" strokeWidth={1.6} />
    <path d="m20 20-3.5-3.5" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
  </svg>
);

export const PlusIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
  </svg>
);

export const XIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="M6 6l12 12M18 6 6 18" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
  </svg>
);

export const EditIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path
      d="M4 20h4l10-10-4-4L4 16v4Z"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinejoin="round"
    />
    <path d="m13 6 4 4" stroke="currentColor" strokeWidth={1.6} />
  </svg>
);

export const TrashIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path
      d="M4 7h16M9 7V5a2 2 0 0 1 2-2h2a2 2 0 0 1 2 2v2M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinejoin="round"
    />
  </svg>
);

export const EyeIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path
      d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"
      stroke="currentColor"
      strokeWidth={1.6}
    />
    <circle cx="12" cy="12" r="3" stroke="currentColor" strokeWidth={1.6} />
  </svg>
);

export const LogoutIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path
      d="M15 4h3a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-3"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
    />
    <path
      d="M10 17 5 12l5-5M5 12h11"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
    />
  </svg>
);

export const WifiIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="M2 9a16 16 0 0 1 20 0M5 12.5a11 11 0 0 1 14 0M8 16a6 6 0 0 1 8 0" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
    <circle cx="12" cy="19" r="1.4" fill="currentColor" />
  </svg>
);

export const WifiOffIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="m2 2 20 20" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
    <path
      d="M8.5 16.5a5 5 0 0 1 7 0M5 12.5a11 11 0 0 1 5-2.3M19 12.5a11 11 0 0 0-5-2.3M2 9a16 16 0 0 1 6.4-2.5M22 9a16 16 0 0 0-3.4-1.5"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
    />
  </svg>
);

export const GaugeIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="M3 14a9 9 0 1 1 18 0" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
    <path d="m12 14 4-3" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
    <circle cx="12" cy="14" r="1.3" fill="currentColor" />
  </svg>
);

export const BatteryIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <rect x="3" y="8" width="16" height="8" rx="2" stroke="currentColor" strokeWidth={1.6} />
    <path d="M21 11v2" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
    <path d="M11 10l-3 4h3l-1 3 3-4h-3l1-3Z" fill="currentColor" />
  </svg>
);

export const ClockIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <circle cx="12" cy="12" r="9" stroke="currentColor" strokeWidth={1.6} />
    <path d="M12 7v5l3 2" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
  </svg>
);

export const UserIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <circle cx="12" cy="8" r="4" stroke="currentColor" strokeWidth={1.6} />
    <path d="M4 21a8 8 0 0 1 16 0" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
  </svg>
);

export const LockIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <rect x="4" y="11" width="16" height="9" rx="2" stroke="currentColor" strokeWidth={1.6} />
    <path d="M8 11V8a4 4 0 1 1 8 0v3" stroke="currentColor" strokeWidth={1.6} />
  </svg>
);

export const ChevronLeftIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="m15 6-6 6 6 6" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export const ChevronRightIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="m9 6 6 6-6 6" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export const CheckIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="m5 12 5 5L20 7" stroke="currentColor" strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export const RefreshIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="M4 12a8 8 0 0 1 14-5.3L20 9" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
    <path d="M20 4v5h-5" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
    <path d="M20 12a8 8 0 0 1-14 5.3L4 15" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
    <path d="M4 20v-5h5" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export const SignalIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="M4 20h2v-4H4v4Zm5 0h2v-7H9v7Zm5 0h2v-10h-2v10Zm5 0h2V6h-2v14Z" fill="currentColor" />
  </svg>
);

export const CarFrontIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path
      d="M5 16V8l2-4h10l2 4v8"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinejoin="round"
    />
    <path
      d="M3 16h18M5 16v3h2v-3M17 16v3h2v-3"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinejoin="round"
    />
    <circle cx="8" cy="13" r="1" fill="currentColor" />
    <circle cx="16" cy="13" r="1" fill="currentColor" />
  </svg>
);

export const SparkleIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path
      d="M12 3v4M12 17v4M3 12h4M17 12h4M6 6l2.5 2.5M15.5 15.5 18 18M6 18l2.5-2.5M15.5 8.5 18 6"
      stroke="currentColor"
      strokeWidth={1.6}
      strokeLinecap="round"
    />
  </svg>
);

export const ChevronDownIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="m6 9 6 6 6-6" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" strokeLinejoin="round" />
  </svg>
);

export const ZoomInIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="M12 5v14M5 12h14" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
  </svg>
);

export const ZoomOutIcon = (props: IconProps) => (
  <svg {...baseProps} {...props}>
    <path d="M5 12h14" stroke="currentColor" strokeWidth={1.6} strokeLinecap="round" />
  </svg>
);
