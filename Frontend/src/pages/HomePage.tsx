import { Link } from 'react-router-dom';

export function HomePage() {
  return (
    <div className="page home-page">
      <div className="home-orb home-orb-one" aria-hidden="true" />
      <div className="home-orb home-orb-two" aria-hidden="true" />

      <section className="hero home-hero">
        <div className="home-hero-glow" aria-hidden="true" />
        <div className="home-hero-mark" aria-hidden="true">
          <span className="material-symbols-outlined">spa</span>
        </div>
        <div className="home-hero-content">
          <div className="home-badge">
            <span className="material-symbols-outlined">auto_awesome</span>
            AI-powered intellectual property knowledge assistant
          </div>
          <p className="eyebrow">India's trusted IP knowledge companion</p>
          <h1><span>IP Knowledge,</span><span>Made Easier to Understand.</span></h1>
          <p className="hero-copy">Find evidence-backed answers from authoritative intellectual property and regulatory sources.</p>
          <div className="button-row">
            <Link className="button primary home-primary-action" to="/ask">
              <span className="material-symbols-outlined">search</span>
              Ask an IP Question
              <span className="material-symbols-outlined action-arrow">arrow_forward</span>
            </Link>
            <Link className="button secondary" to="/regulatory">
              <span className="material-symbols-outlined">explore</span>
              Explore Regulatory Tools
            </Link>
          </div>
          <div className="home-proof-row">
            <span><span className="material-symbols-outlined">verified</span> Evidence-grounded</span>
            <span><span className="material-symbols-outlined">language</span> Built for India</span>
            <span><span className="material-symbols-outlined">translate</span> Multilingual support</span>
          </div>
        </div>
      </section>

      <section className="home-capabilities" aria-label="Core capabilities">
        <div className="home-section-heading">
          <div>
            <p className="eyebrow">Your path to clarity</p>
            <h2>Explore what Sahayak can do</h2>
          </div>
          <span className="home-section-note">Simple tools. Authoritative direction.</span>
        </div>
        <div className="capability-grid">
        <Link className="capability-card" to="/ask">
          <span className="capability-number">01</span>
          <div className="card-icon">
            <span className="material-symbols-outlined">help_center</span>
          </div>
          <h2>Ask IP Questions</h2>
          <p>Understand Indian IP laws and procedures using authoritative sources.</p>
          <span className="capability-link">Start exploring <span className="material-symbols-outlined">arrow_forward</span></span>
        </Link>
        <Link className="capability-card" to="/formulations">
          <span className="capability-number">02</span>
          <div className="card-icon">
            <span className="material-symbols-outlined">science</span>
          </div>
          <h2>Formulation Review</h2>
          <p>Classify formulations into the supported regulatory categories.</p>
          <span className="capability-link">Review a product <span className="material-symbols-outlined">arrow_forward</span></span>
        </Link>
        <Link className="capability-card" to="/regulatory">
          <span className="capability-number">03</span>
          <div className="card-icon">
            <span className="material-symbols-outlined">policy</span>
          </div>
          <h2>Regulatory Analysis</h2>
          <p>Review traditional knowledge, known ingredients, ABS and GRATK considerations.</p>
          <span className="capability-link">See regulatory routes <span className="material-symbols-outlined">arrow_forward</span></span>
        </Link>
        </div>
      </section>

      <div className="home-bottom-ribbon" aria-label="IP-SAKTI Sahayak service note">
        <span className="material-symbols-outlined">balance</span>
        <span>Clarity for every inventor, researcher, and traditional knowledge holder.</span>
        <span className="home-ribbon-dot" aria-hidden="true" />
        <span>Powered by verified sources</span>
      </div>
    </div>
  );
}
