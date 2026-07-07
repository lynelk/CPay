import React from 'react';

const defaultButtons = {
  alert: [{ text: 'Ok', value: true }],
  confirm: [{ text: 'Ok', value: true }, { text: 'Cancel', value: false }],
};

class StableMessager extends React.Component {
  constructor(props) {
    super(props);
    this.state = {
      open: false,
      title: '',
      msg: '',
      icon: null,
      content: null,
      buttons: [],
      result: null,
    };
  }

  alert(options = {}) {
    this.open({
      ...options,
      buttons: options.buttons && options.buttons.length ? options.buttons : defaultButtons.alert,
      icon: options.icon || 'info',
    });
  }

  confirm(options = {}) {
    this.open({
      ...options,
      buttons: options.buttons && options.buttons.length ? options.buttons : defaultButtons.confirm,
      icon: options.icon || 'question',
    });
  }

  close(button = null) {
    const { result } = this.state;
    const value = button ? button.value : null;

    this.setState({ open: false, result: null }, () => {
      if (typeof result === 'function') {
        result(value);
      }
    });
  }

  open(options) {
    this.setState({
      open: true,
      title: options.title || '',
      msg: options.msg || '',
      icon: options.icon || null,
      content: options.content || null,
      buttons: options.buttons || defaultButtons.alert,
      result: options.result || null,
    });
  }

  renderIcon() {
    const { icon } = this.state;
    if (!icon) {
      return null;
    }
    return <div className={`cpay-stable-messager-icon cpay-stable-messager-icon-${icon}`} aria-hidden="true">i</div>;
  }

  renderBody() {
    const { content, msg } = this.state;
    if (typeof content === 'function') {
      return content(this.state);
    }
    return (
      <div className="cpay-stable-messager-message">
        {this.renderIcon()}
        <div>{msg}</div>
      </div>
    );
  }

  render() {
    if (!this.state.open) {
      return null;
    }

    return (
      <div className="cpay-stable-messager-layer" role="presentation">
        <div className="cpay-stable-messager-mask" aria-hidden="true" />
        <section
          className="cpay-stable-messager-window"
          role="dialog"
          aria-modal="true"
          aria-labelledby="cpay-stable-messager-title">
          <header className="cpay-stable-messager-header">
            <h2 id="cpay-stable-messager-title">{this.state.title}</h2>
            <button type="button" className="cpay-stable-messager-close" aria-label="Close dialog" onClick={() => this.close()}>
              ×
            </button>
          </header>
          <div className="cpay-stable-messager-body">
            {this.renderBody()}
          </div>
          <footer className="cpay-stable-messager-actions">
            {this.state.buttons.map((button, index) => (
              <button
                type="button"
                className="cpay-stable-messager-action"
                key={`${button.text}-${index}`}
                onClick={() => this.close(button)}>
                {button.text}
              </button>
            ))}
          </footer>
        </section>
      </div>
    );
  }
}

export default StableMessager;
