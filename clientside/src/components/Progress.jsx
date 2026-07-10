import React from 'react';
import { ProgressOverlay } from '../ui';

/**
 * "Please wait…" overlay. Now backed by the iOS ProgressOverlay primitive
 * (replaces rc-easyui ProgressBar + Dialog). Keeps the incrementing bar
 * behaviour while a loader is active.
 */
class Progress extends React.Component {
  constructor(props) {
    super(props);
    this.state = { progressValue: this.props.progressValue || 0 };
    this.timeIntervalFunc = null;
  }

  componentDidMount() {
    this.startTicker();
  }

  componentWillUnmount() {
    if (this.timeIntervalFunc) clearInterval(this.timeIntervalFunc);
  }

  startTicker() {
    this.timeIntervalFunc = setInterval(() => {
      this.setState((prev) => ({ progressValue: prev.progressValue >= 100 ? 0 : prev.progressValue + 10 }));
    }, 800);
  }

  render() {
    if (!this.props.loaderState) {
      return null;
    }
    return <ProgressOverlay open message="Please wait…" value={this.state.progressValue} />;
  }
}

export default Progress;
