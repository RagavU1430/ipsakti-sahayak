import { Link } from 'react-router-dom';

export function HomePage() {
  return (
    <div className="page home-page">
      <section className="hero">
        <p className="eyebrow">AI-powered intellectual property knowledge assistant</p>
        <h1>IP Knowledge, Made Easier to Understand.</h1>
        <p className="hero-copy">Find evidence-backed answers from authoritative intellectual property and regulatory sources.</p>
        <div className="button-row">
          <Link className="button primary" to="/ask">
            <span className="material-symbols-outlined">search</span>
            Ask an IP Question
          </Link>
          <Link className="button secondary" to="/regulatory">
            <span className="material-symbols-outlined">explore</span>
            Explore Regulatory Tools
          </Link>
        </div>
      </section>

      <section className="capability-grid" aria-label="Core capabilities">
        <Link className="capability-card" to="/ask">
          <div className="card-icon">
            <span className="material-symbols-outlined">help_center</span>
          </div>
          <h2>Ask IP Questions</h2>
          <p>Understand Indian IP laws and procedures using authoritative sources.</p>
        </Link>
        <Link className="capability-card" to="/formulations">
          <div className="card-icon">
            <span className="material-symbols-outlined">science</span>
          </div>
          <h2>Formulation Review</h2>
          <p>Classify formulations into the supported regulatory categories.</p>
        </Link>
        <Link className="capability-card" to="/regulatory">
          <div className="card-icon">
            <span className="material-symbols-outlined">policy</span>
          </div>
          <h2>Regulatory Analysis</h2>
          <p>Review traditional knowledge, known ingredients, ABS and GRATK considerations.</p>
        </Link>
      </section>
    </div>
  );
}
