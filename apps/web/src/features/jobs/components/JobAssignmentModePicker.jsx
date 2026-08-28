import './JobAssignmentModePicker.css'

function JobAssignmentModePicker({
  assignmentMode,
  priceNegotiationEnabled,
  price,
  disabled = false,
  onModeChange,
  onNegotiationChange,
}) {
  const proposalMode = assignmentMode === 'PROPOSALS'

  return (
    <fieldset className="job-assignment-picker" disabled={disabled}>
      <legend>Jak wybieramy wykonawcę?</legend>
      <div className="job-assignment-picker__options">
        <label className={`job-assignment-option ${assignmentMode === 'INSTANT' ? 'job-assignment-option--selected' : ''}`}>
          <input
            type="radio"
            name="assignmentMode"
            value="INSTANT"
            checked={assignmentMode === 'INSTANT'}
            onChange={() => onModeChange('INSTANT')}
          />
          <span className="job-assignment-option__content">
            <strong>Kto pierwszy, ten bierze</strong>
            <span>Najprostszy tryb dla zakupów, odbioru paczki i szybkich drobnych zadań.</span>
            <small>Pierwszy uprawniony wykonawca może od razu rozpocząć realizację za podaną cenę.</small>
          </span>
        </label>

        <label className={`job-assignment-option ${proposalMode ? 'job-assignment-option--selected' : ''}`}>
          <input
            type="radio"
            name="assignmentMode"
            value="PROPOSALS"
            checked={proposalMode}
            onChange={() => onModeChange('PROPOSALS')}
          />
          <span className="job-assignment-option__content">
            <strong>Chcę wybrać wykonawcę</strong>
            <span>Kandydaci wysyłają prywatne propozycje, a Ty wybierasz konkretną osobę.</span>
            <small>Dobre, gdy liczy się doświadczenie, termin, profil albo chcesz najpierw przeczytać wiadomość wykonawcy.</small>
          </span>
        </label>
      </div>

      {proposalMode && (
        <div className="job-assignment-picker__proposal-settings">
          <label className="job-assignment-picker__negotiation">
            <input
              type="checkbox"
              checked={priceNegotiationEnabled}
              onChange={(event) => onNegotiationChange(event.target.checked)}
            />
            <span>
              <strong>Pozwól proponować inną cenę</strong>
              <small>Wyłączone = wybierasz człowieka, ale każdy proponuje dokładnie opublikowane wynagrodzenie.</small>
            </span>
          </label>

          <div className="job-assignment-picker__escrow-note">
            Przy publikacji blokujemy w escrow podaną kwotę{price ? ` (${formatPrice(price)})` : ''}. Jeśli później wybierzesz wyższą propozycję, system najpierw poprosi portfel o brakującą różnicę. Przy niższej propozycji nadwyżka wróci do salda.
          </div>
        </div>
      )}
    </fieldset>
  )
}

function formatPrice(value) {
  const numeric = Number(value)
  if (!Number.isFinite(numeric)) return value
  return new Intl.NumberFormat('pl-PL', { style: 'currency', currency: 'PLN' }).format(numeric)
}

export default JobAssignmentModePicker
