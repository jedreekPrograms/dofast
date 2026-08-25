function JobCard({ title, description, price }) {
  return (
    <article>
      <h3>{title}</h3>
      <p>{description}</p>
      <strong>{price}</strong>
    </article>
  )
}

export default JobCard
