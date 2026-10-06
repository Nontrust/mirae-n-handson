import { API_BASE } from './api/client';
import { ItemBrowser } from './components/ItemBrowser';
import { ItemSearch } from './components/ItemSearch';

export function App() {
  return (
    <div className="app">
      <header className="app__header">
        <h1>문항 은행</h1>
        <p className="app__api">
          API: <code>{API_BASE}</code>
        </p>
      </header>
      <main>
        <ItemBrowser />
        <ItemSearch />
      </main>
    </div>
  );
}
