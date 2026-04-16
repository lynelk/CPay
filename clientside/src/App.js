import React from 'react';
//mport logo from './logo.svg';
import './App.css';
import { DataGrid, GridColumn } from 'rc-easyui';
import Login from './components/Login';
import Routers from './Routers';

class App extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      loading: true
    }
  }

  componentDidMount() {
    this.setState({ loading: false });
  }
  
  render() {
    const { loading } = this.state;

    if(loading) { // if your component doesn't have to wait for an async action, remove this block 
      return null; // render null when app is not ready
    }

    return (
      <div>
        <Routers />
      </div>
    );
  }
}
 
export default App;
