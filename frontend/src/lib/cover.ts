const gradients = [
  'from-rose-900 to-slate-900',
  'from-amber-900 to-stone-900',
  'from-emerald-900 to-slate-900',
  'from-cyan-900 to-slate-900',
  'from-violet-900 to-slate-900',
  'from-fuchsia-900 to-slate-900',
  'from-orange-900 to-neutral-900',
  'from-indigo-900 to-slate-900'
];

export function gradientFor(title: string, id: number): string {
  const hash = title.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0) + id;
  return gradients[hash % gradients.length];
}

export function initials(title: string): string {
  const words = title.split(/[\s_.-]+/).filter(Boolean);
  if (words.length === 0) return 'BK';
  if (words.length === 1) return words[0].slice(0, 2).toUpperCase();
  return (words[0][0] + words[1][0]).toUpperCase();
}
