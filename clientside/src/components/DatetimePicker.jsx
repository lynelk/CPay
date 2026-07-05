import React from 'react';
import { DateBox, TimeSpinner } from 'rc-easyui';
import common from "./Common";
class DatetimePicker extends React.Component {
    constructor(props) {
      super(props);
      this.state = {
            value: "",
            dateValue: "",
            dateV: new Date(),
            timeValue: this.getDefaultTime(),
            inputStyle: {
                textAlign: "center",
                marginLeft: "5px",
                width: "60px",
                float: "left"
            }
      }
    }
  
    componentDidUpdate() {
        
    }

    shouldComponentUpdate(nextProps, nextState) {
      return true;
    }
  
    componentDidMount() {
        this.setState({
            value : this.props.value
        });
    }

    handleTimeChange(time) {
        let formated_date = common.formatDate(this.state.dateV);
        this.setState({ 
            timeValue: time,
            value: formated_date+" "+time+":00"
        }, () => {
            this.props.onValueSelected(this.state.value);
        });
    }

    formatDate(date) {
        if (date == null) {
            date = new Date();
        }
        let y = date.getFullYear();
        let m = date.getMonth() + 1;
        let d = date.getDate();
        return [y, m, d].join('-');
    }

    getDefaultTime() {
        let date = new Date();
        return date.getHours()+":"+date.getMinutes()
    }

    handleDateChange(date) {
        let formated_date = common.formatDate(date);
        this.setState({ 
            dateValue: formated_date,
            value: formated_date+" "+this.state.timeValue+":00",
            dateV: date,
        }, () => {
            this.props.onValueSelected(this.state.value);
        });
    }
  
    render() {
        const { timeValue, inputStyle } = this.state;
        const tsProps = {
            inputStyle: inputStyle,
            value: timeValue,
            onChange: this.handleTimeChange.bind(this)
        }
        return (
            <div>
                <div style={{ marginBottom: 20 }}>
                    <DateBox 
                        format="yyyy-MM-dd"
                        panelStyle={{width:250,height:300}}
                        value={this.state.dateV} 
                        onChange={this.handleDateChange.bind(this)}
                        style={{
                            float: "left"
                        }}
                        />
                    <TimeSpinner 
                        spinAlign="horizontal" {...tsProps}></TimeSpinner>
                </div>
            </div>
        );
    }
  }

  export default DatetimePicker;