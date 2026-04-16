import React from 'react';
import { ProgressBar,Dialog } from 'rc-easyui';

class Progress extends React.Component {
    
    constructor(props) {
      super(props);
      this.state = {
        progressValue: (this.props.progressValue ? this.props.progressValue : 0),
        disabled: false
      }
      this.timeIntervalFunc = null;
    }


    componentDidMount() {
        this.showLoader();
    } 
    

    showLoader() {
        let timeout = 800;
        this.timeIntervalFunc = setInterval(() => {
            let value = this.state.progressValue;
            value += 10;
            this.setState({ progressValue: value }, ()=> {
                if (value > 100) {
                    this.setState({ progressValue: 0});
                } else {
                    //this.showLoader();
                }
            });
        }, timeout);
    }

    render() {
        if (!this.props.loaderState) {
            //clearInterval(this.timeIntervalFunc);
            return (<div></div>);
        } else {
            //this.showLoader();
            return (
                <div>
                    <Dialog 
                        style={{width:'400px',height:'100px'}}
                        closable={false}
                        borderType="none" modal>
                        <div style={{ margin: '20px' }} align="center">
                            <span>Please wait...</span>
                            <ProgressBar style={{width:'100'}} value={this.state.progressValue}>
                            </ProgressBar>
                        </div>
                    </Dialog>
                </div>
            );
        }
    }
  }
   
  export default Progress;