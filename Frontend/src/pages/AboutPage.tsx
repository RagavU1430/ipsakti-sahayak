export function AboutPage() {
  return (
    <div className="page narrow-page">
      <section className="panel readable">
        <p className="eyebrow">About</p>
        <h1>About IP-SAKTI Sahayak</h1>
        <p>IP-SAKTI Sahayak is a digital legal information service for intellectual property and related regulatory questions.</p>
        <p>It is designed to retrieve from the available knowledge corpus first, present confidence clearly, and show the citations and source identifiers returned by the backend.</p>
        <h2>What it can help with</h2>
        <ul>
          <li>Indian patents, trademarks, copyright, designs, GI and plant variety questions.</li>
          <li>Traditional knowledge and biodiversity-related regulatory review.</li>
          <li>Formulation classification support for the five configured categories.</li>
          <li>Multilingual requests in English, Hindi and Tamil when backend translation is configured.</li>
        </ul>
        <h2>Important limitation</h2>
        <p>This product provides evidence-backed information, not legal advice. For filings, disputes, or commercial decisions, consult a qualified professional.</p>
      </section>
    </div>
  );
}
