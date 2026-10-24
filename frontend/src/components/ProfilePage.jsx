import { useEffect, useState } from 'react';
import { api, initials, jsonBody } from '../api.js';

export default function ProfilePage({ session }) {
  const [form, setForm] = useState({ name: '', phone: '' });
  const [patient, setPatient] = useState(null);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');

  useEffect(() => {
    let active = true;
    api('/api/patients/me').then((data) => { if (active) { setPatient(data); setForm({ name: data.name || '', phone: data.phone || '' }); } })
      .catch((err) => { if (active) setError(err.message); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, []);

  async function submit(event) {
    event.preventDefault(); setBusy(true); setError(''); setSuccess('');
    try { const result = await api('/api/patients/me', { method: 'PUT', body: jsonBody(form) }); setPatient(result); setForm({ name: result.name || '', phone: result.phone || '' }); setSuccess('Your profile was updated.'); }
    catch (err) { setError(err.message); }
    finally { setBusy(false); }
  }

  return <section className="content-section"><div className="section-heading-row"><div><span className="eyebrow">ACCOUNT SETTINGS</span><h2>My profile</h2><p className="muted">Keep your contact information up to date.</p></div></div>
    {error && <div className="alert alert-error">{error}</div>}{success && <div className="alert alert-success">{success}</div>}
    {loading ? <div className="panel-card loading-line">Loading profile…</div> : <div className="profile-layout"><div className="panel-card profile-summary"><div className="profile-avatar">{initials(patient?.name || session.email)}</div><h3>{patient?.name || 'Patient'}</h3><p className="muted">{patient?.email || session.email}</p><span className="role-label">PATIENT ACCOUNT</span><div className="profile-divider" /><div className="profile-detail"><span>Email</span><strong>{patient?.email || session.email}</strong></div><div className="profile-detail"><span>Patient ID</span><strong>{patient?.id ? `#${patient.id}` : '—'}</strong></div></div>
      <form className="panel-card profile-form" onSubmit={submit}><span className="eyebrow">PERSONAL INFORMATION</span><h3>Profile details</h3><p className="muted">Your email is used for sign in and cannot be changed here.</p><label>Full name<input value={form.name} onChange={(e) => setForm((old) => ({ ...old, name: e.target.value }))} required maxLength={120} /></label><label>Email address<input value={patient?.email || session.email} disabled /></label><label>Phone number<input value={form.phone} onChange={(e) => setForm((old) => ({ ...old, phone: e.target.value }))} required minLength={7} maxLength={25} /></label><button className="btn btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save changes'} <span>→</span></button></form></div>}
  </section>;
}
