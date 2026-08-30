interface Props {
  size?: 'sm' | 'md' | 'lg';
  className?: string;
}

const SIZES = {
  sm: { icon: 'h-5 w-5', text: 'text-lg' },
  md: { icon: 'h-7 w-7 md:h-9 md:w-9', text: 'text-2xl md:text-4xl' },
  lg: { icon: 'h-10 w-10', text: 'text-4xl' }
};

export default function Logo({ size = 'md', className = '' }: Props) {
  const s = SIZES[size];
  return (
    <span className={`inline-flex items-center gap-2 ${className}`}>
      <svg className={`${s.icon} shrink-0 text-booki-accent`} viewBox="0 0 24 24" fill="none">
        <path
          d="M12 5.2C9.7 3.7 6.8 3.2 4.3 3.7v13.6c2.5-.5 5.4 0 7.7 1.5"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path
          d="M12 5.2c2.3-1.5 5.2-2 7.7-1.5v13.6c-2.5-.5-5.4 0-7.7 1.5"
          stroke="currentColor"
          strokeWidth="1.7"
          strokeLinecap="round"
          strokeLinejoin="round"
        />
        <path d="M12 5.2v13.6" stroke="currentColor" strokeWidth="1.7" strokeLinecap="round" />
        <path
          d="M18 5.6l.7-1.6.6 1.6 1.6.6-1.6.6-.6 1.6-.7-1.6-1.5-.6z"
          fill="currentColor"
        />
      </svg>
      <span className={`${s.text} font-logo text-white`}>
        Boo<span className="text-booki-accent">KI</span>
      </span>
    </span>
  );
}
