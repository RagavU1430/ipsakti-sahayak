import { useState, useEffect, useCallback } from 'react';

type Theme = 'light' | 'dark' | 'system';

export function useTheme() {
  const [theme, setTheme] = useState<Theme>(() => {
    // Try to get saved preference
    const saved = localStorage.getItem('ipsakti-theme') as Theme | null;
    return saved || 'system';
  });

  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>(() => {
    const saved = localStorage.getItem('ipsakti-theme') as Theme | null;
    if (saved === 'light' || saved === 'dark') return saved;
    // Check system preference
    if (window.matchMedia('(prefers-color-scheme: dark)').matches) return 'dark';
    return 'light';
  });

  // Update document attribute and resolved theme
  const applyTheme = useCallback((newTheme: Theme) => {
    let resolved: 'light' | 'dark';

    if (newTheme === 'system') {
      resolved = window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    } else {
      resolved = newTheme;
    }

    setResolvedTheme(resolved);

    // Apply to document
    document.documentElement.setAttribute('data-theme', resolved);
    localStorage.setItem('ipsakti-theme', newTheme);
  }, []);

  // Initialize theme on mount
  useEffect(() => {
    applyTheme(theme);

    // Listen for system theme changes when in system mode
    if (theme === 'system') {
      const mediaQuery = window.matchMedia('(prefers-color-scheme: dark)');
      const handler = () => applyTheme('system');
      mediaQuery.addEventListener('change', handler);
      return () => mediaQuery.removeEventListener('change', handler);
    }
  }, [theme, applyTheme]);

  const toggleTheme = useCallback(() => {
    setTheme(current => {
      // When toggling, cycle through: light -> dark -> system -> light
      const next: Theme = current === 'light' ? 'dark' : current === 'dark' ? 'system' : 'light';
      applyTheme(next);
      return next;
    });
  }, [applyTheme]);

  const setThemePreference = useCallback((newTheme: Theme) => {
    setTheme(newTheme);
    applyTheme(newTheme);
  }, [applyTheme]);

  return {
    theme,
    resolvedTheme,
    toggleTheme,
    setTheme: setThemePreference,
    isDark: resolvedTheme === 'dark',
  };
}
