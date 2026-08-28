import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from '../../auth/AuthContext.js'
import { getJobCategories } from '../../jobs/api/jobsApi.js'
import ServiceAreaEditor from '../components/ServiceAreaEditor.jsx'
import { getMyServiceCategories, updateMyServiceCategories } from '../api/profileApi.js'
import './ProfilePage.css'

const FULFILLMENT_LABELS = {
  ON_SITE: 'Na miejscu',
  POINT_TO_POINT: 'Transport A → B',
}

function ProfilePage() {
  const { user, updateProfile, changePassword } = useAuth()
  const [nickname, setNickname] = useState(user.nickname)
  const [bio, setBio] = useState(user.bio || '')
  const [publicLocation, setPublicLocation] = useState(user.publicLocation || '')
  const [profileMessage, setProfileMessage] = useState('')
  const [passwords, setPasswords] = useState({ currentPassword: '', newPassword: '' })
  const [passwordMessage, setPasswordMessage] = useState('')
  const [serviceGroups, setServiceGroups] = useState([])
  const [selectedCategoryIds, setSelectedCategoryIds] = useState([])
  const [specializationMessage, setSpecializationMessage] = useState('')
  const [specializationsLoading, setSpecializationsLoading] = useState(true)
  const [savingSpecializations, setSavingSpecializations] = useState(false)
  const [savingProfile, setSavingProfile] = useState(false)
  const [savingPassword, setSavingPassword] = useState(false)

  useEffect(() => {
    let active = true
    Promise.all([getJobCategories(), getMyServiceCategories()])
      .then(([catalog, selected]) => {
        if (!active) return
        setServiceGroups((catalog || [])
          .map((group) => ({
            ...group,
            children: (group.children || []).filter((category) => category.fulfillmentMode),
          }))
          .filter((group) => group.children.length > 0))
        setSelectedCategoryIds((selected || []).map((category) => category.categoryId))
      })
      .catch((error) => {
        if (active) setSpecializationMessage(error.message || 'Nie udało się pobrać specjalizacji.')
      })
      .finally(() => {
        if (active) setSpecializationsLoading(false)
      })

    return () => { active = false }
  }, [])

  async function saveProfile(event) {
    event.preventDefault()
    setSavingProfile(true)
    setProfileMessage('')
    try {
      const updated = await updateProfile({ nickname, bio, publicLocation })
      setNickname(updated.nickname)
      setBio(updated.bio || '')
      setPublicLocation(updated.publicLocation || '')
      setProfileMessage('Profil został zapisany.')
    } catch (error) {
      setProfileMessage(error.message)
    } finally {
      setSavingProfile(false)
    }
  }

  async function savePassword(event) {
    event.preventDefault()
    setSavingPassword(true)
    setPasswordMessage('')
    try {
      await changePassword(passwords)
      setPasswords({ currentPassword: '', newPassword: '' })
      setPasswordMessage('Hasło zostało zmienione.')
    } catch (error) {
      setPasswordMessage(error.message)
    } finally {
      setSavingPassword(false)
    }
  }

  function toggleSpecialization(categoryId) {
    setSpecializationMessage('')
    if (selectedCategoryIds.includes(categoryId)) {
      setSelectedCategoryIds((current) => current.filter((id) => id !== categoryId))
      return
    }
    if (selectedCategoryIds.length >= 10) {
      setSpecializationMessage('Możesz wybrać maksymalnie 10 specjalizacji.')
      return
    }
    setSelectedCategoryIds((current) => [...current, categoryId])
  }

  async function saveSpecializations() {
    setSavingSpecializations(true)
    setSpecializationMessage('')
    try {
      const updated = await updateMyServiceCategories(selectedCategoryIds)
      setSelectedCategoryIds(updated.map((category) => category.categoryId))
      setSpecializationMessage('Specjalizacje zostały zapisane i są widoczne na publicznym profilu.')
    } catch (error) {
      setSpecializationMessage(error.message || 'Nie udało się zapisać specjalizacji.')
    } finally {
      setSavingSpecializations(false)
    }
  }

  return (
    <main className="account-page">
      <header className="page-heading page-heading--row">
        <div>
          <span className="eyebrow">Konto</span>
          <h1>Profil i ustawienia</h1>
          <p>Zarządzaj podstawowymi danymi konta, publiczną wizytówką i bezpieczeństwem logowania.</p>
        </div>
        <div className="profile-actions">
          <Link className="button button--secondary" to="/verification">Weryfikacja tożsamości</Link>
          <Link className="button button--secondary" to={`/users/${user.id}`}>Zobacz publiczny profil</Link>
        </div>
      </header>

      <div className="account-grid">
        <section className="panel">
          <h2>Dane konta i wizytówka</h2>
          <dl className="account-summary">
            <div><dt>Email</dt><dd>{user.email}</dd></div>
            <div><dt>Rola</dt><dd>{user.role === 'ADMIN' ? 'Administrator' : 'Użytkownik'}</dd></div>
            <div><dt>Status</dt><dd>{user.status === 'ACTIVE' ? 'Aktywne' : 'Zawieszone'}</dd></div>
          </dl>
          <form className="form-stack" onSubmit={saveProfile}>
            <label className="field">
              <span>Nazwa użytkownika</span>
              <input value={nickname} onChange={(event) => setNickname(event.target.value)} minLength={3} maxLength={80} required />
            </label>
            <label className="field">
              <span>Publiczna lokalizacja <small>(opcjonalnie)</small></span>
              <input
                value={publicLocation}
                onChange={(event) => setPublicLocation(event.target.value)}
                maxLength={120}
                placeholder="np. Wrocław i okolice"
              />
            </label>
            <label className="field">
              <span>O mnie / zakres usług <small>(opcjonalnie)</small></span>
              <textarea
                className="profile-bio-input"
                value={bio}
                onChange={(event) => setBio(event.target.value)}
                maxLength={600}
                rows={6}
                placeholder="Napisz krótko, w czym się specjalizujesz i jakie masz doświadczenie."
              />
              <small className="profile-field-hint">Te informacje będą widoczne publicznie. {bio.length}/600</small>
            </label>
            {profileMessage && <div className="form-message">{profileMessage}</div>}
            <button className="button button--primary" type="submit" disabled={savingProfile}>Zapisz profil</button>
          </form>
        </section>

        <section className="panel">
          <h2>Zmień hasło</h2>
          <form className="form-stack" onSubmit={savePassword}>
            <label className="field">
              <span>Aktualne hasło</span>
              <input type="password" value={passwords.currentPassword} onChange={(event) => setPasswords((current) => ({ ...current, currentPassword: event.target.value }))} autoComplete="current-password" required />
            </label>
            <label className="field">
              <span>Nowe hasło</span>
              <input type="password" value={passwords.newPassword} onChange={(event) => setPasswords((current) => ({ ...current, newPassword: event.target.value }))} minLength={8} maxLength={72} autoComplete="new-password" required />
            </label>
            {passwordMessage && <div className="form-message">{passwordMessage}</div>}
            <button className="button button--secondary" type="submit" disabled={savingPassword}>Zmień hasło</button>
          </form>
        </section>

        <section className="panel account-specializations">
          <div className="account-specializations__heading">
            <div>
              <span className="eyebrow">Publiczna oferta</span>
              <h2>Specjalizacje usług</h2>
              <p>Wybierz do 10 konkretnych usług, które realizujesz. Kategorie są publiczne, ale nie zmieniają automatycznie Twojej lokalizacji ani dostępności.</p>
            </div>
            <strong>{selectedCategoryIds.length}/10</strong>
          </div>

          {specializationsLoading && <div className="page-state">Pobieranie katalogu usług…</div>}
          {!specializationsLoading && (
            <div className="specialization-groups">
              {serviceGroups.map((group) => (
                <fieldset className="specialization-group" key={group.id}>
                  <legend>{group.name}</legend>
                  <div className="specialization-options">
                    {group.children.map((category) => {
                      const checked = selectedCategoryIds.includes(category.id)
                      return (
                        <label className={`specialization-option ${checked ? 'specialization-option--selected' : ''}`} key={category.id}>
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() => toggleSpecialization(category.id)}
                          />
                          <span>
                            <strong>{category.name}</strong>
                            <small>{FULFILLMENT_LABELS[category.fulfillmentMode] || category.fulfillmentMode}</small>
                          </span>
                        </label>
                      )
                    })}
                  </div>
                </fieldset>
              ))}
            </div>
          )}

          {specializationMessage && <div className="form-message">{specializationMessage}</div>}
          <div>
            <button
              className="button button--primary"
              type="button"
              disabled={specializationsLoading || savingSpecializations}
              onClick={saveSpecializations}
            >
              {savingSpecializations ? 'Zapisywanie…' : 'Zapisz specjalizacje'}
            </button>
          </div>
        </section>

        <ServiceAreaEditor />
      </div>
    </main>
  )
}

export default ProfilePage
