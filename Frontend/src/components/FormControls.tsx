import type { Jurisdiction, Language } from '../api/types';

export function JurisdictionSelect({ value, onChange }: { value: Jurisdiction; onChange: (value: Jurisdiction) => void }) {
  return (
    <label className="field compact-field">
      <span>Jurisdiction</span>
      <select value={value} onChange={(event) => onChange(event.target.value as Jurisdiction)}>
        <option value="INDIA">India</option>
        <option value="INTERNATIONAL">International</option>
        <option value="AUTO">Auto detect</option>
      </select>
    </label>
  );
}

export function LanguageSelect({ value, onChange }: { value: Language; onChange: (value: Language) => void }) {
  return (
    <label className="field compact-field">
      <span>Language</span>
      <select value={value} onChange={(event) => onChange(event.target.value as Language)}>
        <option value="en">English — English</option>
        <option value="hi">हिन्दी — Hindi</option>
        <option value="ta">தமிழ் — Tamil</option>
        <option value="te">తెలుగు — Telugu</option>
        <option value="kn">ಕನ್ನಡ — Kannada</option>
        <option value="ml">മലയാളം — Malayalam</option>
      </select>
    </label>
  );
}

export function TextField(props: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  required?: boolean;
}) {
  return (
    <label className="field">
      <span>{props.label}</span>
      <input
        value={props.value}
        onChange={(event) => props.onChange(event.target.value)}
        placeholder={props.placeholder}
        required={props.required}
      />
    </label>
  );
}

export function TextArea(props: {
  label: string;
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  rows?: number;
  required?: boolean;
}) {
  return (
    <label className="field">
      <span>{props.label}</span>
      <textarea
        value={props.value}
        onChange={(event) => props.onChange(event.target.value)}
        placeholder={props.placeholder}
        rows={props.rows || 4}
        required={props.required}
      />
    </label>
  );
}

export function CheckboxField(props: { label: string; checked: boolean; onChange: (checked: boolean) => void }) {
  return (
    <label className="check-field">
      <input type="checkbox" checked={props.checked} onChange={(event) => props.onChange(event.target.checked)} />
      <span>{props.label}</span>
    </label>
  );
}
