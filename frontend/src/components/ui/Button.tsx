import type { ButtonHTMLAttributes } from 'react';

type Variant = 'primary' | 'secondary' | 'ghost';
type Size = 'sm' | 'md';

interface Props extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: Variant;
  size?: Size;
}

const VARIANT_CLASSES: Record<Variant, string> = {
  primary: 'bg-booki-accent text-white shadow-lg hover:bg-booki-accent-hover disabled:opacity-50',
  secondary: 'bg-booki-card text-white hover:bg-booki-card-hover disabled:opacity-50',
  ghost: 'bg-white/10 text-white hover:bg-white/20 disabled:opacity-40'
};

const SIZE_CLASSES: Record<Size, string> = {
  sm: 'px-3 py-1.5 text-xs',
  md: 'px-5 py-2.5 text-sm'
};

export default function Button({ variant = 'primary', size = 'md', className = '', ...rest }: Props) {
  return (
    <button
      className={`flex items-center justify-center gap-2 rounded-lg font-bold transition ${VARIANT_CLASSES[variant]} ${SIZE_CLASSES[size]} ${className}`}
      {...rest}
    />
  );
}
