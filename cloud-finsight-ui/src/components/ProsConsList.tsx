export function ProsConsList({ pros, cons }: { pros: string; cons: string }) {
  const prosLines = pros
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
  const consLines = cons
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)

  return (
    <div className="pros-cons-list">
      <ul className="pros-list">
        {prosLines.map((line, index) => (
          <li key={index}>✓ {line}</li>
        ))}
      </ul>
      <ul className="cons-list">
        {consLines.map((line, index) => (
          <li key={index}>⚠ {line}</li>
        ))}
      </ul>
    </div>
  )
}
