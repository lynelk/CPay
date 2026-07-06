import React from 'react';
import ReactDOM from 'react-dom';
import App from './App';

jest.mock('./features/OperationsConsole', () => function MockOperationsConsole() {
  return <div />;
});

it('renders without crashing', () => {
  const div = document.createElement('div');
  ReactDOM.render(<App />, div);
  ReactDOM.unmountComponentAtNode(div);
});