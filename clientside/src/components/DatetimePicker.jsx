import React from 'react';
import { DateField } from '../ui';

function pad(n) {
  return String(n).padStart(2, '0');
}

/**
 * Combined date + time picker built on native iOS-styled inputs (replaces
 * rc-easyui DateBox + TimeSpinner). Emits "yyyy-MM-dd HH:mm:ss" via
 * onValueSelected, matching the previous contract.
 */
class DatetimePicker extends React.Component {
  constructor(props) {
    super(props);
    const now = new Date();
    this.state = {
      dateValue: `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`,
      timeValue: `${pad(now.getHours())}:${pad(now.getMinutes())}`,
    };
  }

  emit(next) {
    const { dateValue, timeValue } = { ...this.state, ...next };
    if (dateValue && timeValue && this.props.onValueSelected) {
      this.props.onValueSelected(`${dateValue} ${timeValue}:00`);
    }
  }

  handleDateChange(dateValue) {
    this.setState({ dateValue }, () => this.emit({ dateValue }));
  }

  handleTimeChange(timeValue) {
    this.setState({ timeValue }, () => this.emit({ timeValue }));
  }

  render() {
    return (
      <div style={{ display: 'flex', gap: 'var(--ios-space-3)', flexWrap: 'wrap' }}>
        <DateField
          id="dtp-date"
          kind="date"
          value={this.state.dateValue}
          onValueChange={(v) => this.handleDateChange(v)}
        />
        <DateField
          id="dtp-time"
          kind="time"
          value={this.state.timeValue}
          onValueChange={(v) => this.handleTimeChange(v)}
        />
      </div>
    );
  }
}

export default DatetimePicker;
