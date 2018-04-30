var currency = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 2,
  // the default value for minimumFractionDigits depends on the currency
  // and is usually already 2
});


class App extends React.Component {

    constructor(props) {
        super(props);
        this.state={name:'', running:false, balance: 0, btc: 1, btcHoldings: 0, processing: false};
        this.startGame = this.startGame.bind(this);
        this.restartGame = this.restartGame.bind(this);
        this.updateBtcPrice = this.updateBtcPrice.bind(this);
        this.updateBankBalance = this.updateBankBalance.bind(this);
        this.tradeBtc = this.tradeBtc.bind(this);
    }

    startGame(playerName) {
        //open the bank account
        fetch("/game/bank/open", {
            method: 'post',
            headers: {
            "Content-type": "application/json; charset=UTF-8"
            },
            body: JSON.stringify({name: playerName})
        })
        .then(response => response.json())
        .then(data => {
            this.setState({running: true, name: playerName, balance: data.balance, accountNo: data.accountNo});
            //start updating the bank balance using server sent events
            var bankSrc = new EventSource("/game/bank/balance/watch/" + data.accountNo);
            bankSrc.addEventListener("balance", this.updateBankBalance);
        })
        .catch(function(e) {
            alert("Failed to start game")
        });

        //start updating the bitcoin price using server sent events
        var evtSource = new EventSource("/game/bitcoin/price/watch");
        evtSource.onmessage = this.updateBtcPrice;
    }

    updateBtcPrice(e) {
        this.setState((prevState, props) => ({ btc: e.data }));
    }

    updateBankBalance(e) {
        this.setState((prevState, props) => ({
                 balance: e.data
             }));
    }

    tradeBtc(units) {

        this.setState({processing: true})
        fetch("/game/bitcoin/exchange", {
                method: 'post',
                headers: {
                "Content-type": "application/json; charset=UTF-8"
                },
                body: JSON.stringify({name: this.state.name, bankAccountNo: this.state.accountNo, units: units})
            }).then((response) => {
                    this.setState({processing: false})
                    if (response.status !== 200) {
                      alert("Failed to process trade")
                      return;
                    }
                    response.text().then((data) => {
                      this.setState({btcHoldings : data})
                    });
                  }
                )
              .catch(function(err) {
                this.setState({processing: false})
                alert("Failed to process trade")
              });

    }

    restartGame() {
        this.setState((prevState, props) => ({
            running: false, name: '', balance: 0
        }));
    }

    render() {
        const pageBody = this.state.running ? <RunningGame name={this.state.name} accountNo={this.state.accountNo} balance={this.state.balance} btc={this.state.btc} tradeBtc={this.tradeBtc} btcHoldings={this.state.btcHoldings} processing={this.state.processing}/> : <StartGame start={this.startGame}/>

        return (
          <div className="container-fluid">
            <Header restart={this.restartGame}/>
            {pageBody}
          </div>
        );
      }
}

function Header(props) {
  return (<nav className="navbar navbar-light bg-light">
      <a className="navbar-brand">Crypto Trader</a>
      <form className="form-inline">
          <button className="btn btn-outline-success my-2 my-sm-0" onClick={props.restart} type="button" >Restart Game</button>
      </form>
  </nav>);
}

class StartGame extends React.Component {

  constructor(props) {
      super(props);
      this.state={name:''};
      this.handleChange = this.handleChange.bind(this);
      this.startGame = this.startGame.bind(this);
  }

  handleChange(event) {
      this.setState({name: event.target.value});
  }

  startGame(e) {
      this.props.start(this.state.name)
  }

  render() {
      return (
      <div className="row" id="start">
          <div className="col-sm-4 offset-sm-4">
              <form>
                  <div className="form-group">
                      <label htmlFor="nameInput">Name</label>
                      <input type="text" className="form-control" id="nameInput" aria-describedby="nameHelp" placeholder="Enter your name" value={this.state.name} onChange={this.handleChange} />
                      <small id="nameHelp" className="form-text text-muted">Please enter your name to begin</small>
                  </div>
                  <button type="button" className="btn btn-primary" onClick={this.startGame}>Start Game</button>
              </form>
          </div>
      </div>);
  }
}

class RunningGame extends React.Component {
  constructor(props) {
      super(props);
      this.updateUnits = this.updateUnits.bind(this);
      this.buy = this.buy.bind(this);
      this.sell = this.sell.bind(this);
      this.state = {units: 0}
  }

  updateUnits(e) {
    this.setState({units: e.target.value});
  }

  sell(){
    this.doTrade(-this.state.units);
  }

  buy(){
    this.props.tradeBtc(this.state.units);
  }

    render() {

      return (<div id="game">
      <div className="row">
          <div className="col-sm-4 " >
            <h3>Bank for {this.props.name}</h3>
            <div className="input-group mb-3">
              <div className="input-group-prepend">
                <span className="input-group-text">Balance:</span>
              </div>
              <input type="text" className="form-control" value={currency.format(this.props.balance)} readOnly="true"></input>
            </div>
            <div className="input-group mb-3">
              <div className="input-group-prepend">
                <span className="input-group-text">Net Worth:</span>
              </div>
              <input type="text" className="form-control" value={currency.format(Number(this.props.balance) + (Number(this.props.btc) * Number(this.props.btcHoldings)))} readOnly="true"></input>
            </div>
          </div>
          <div className="col-sm-4">
            <h3>Bitcoin</h3>
            <div className="input-group mb-3">
              <div className="input-group-prepend">
                <span className="input-group-text">Price:</span>
              </div>
              <input type="text" className="form-control" value={currency.format(this.props.btc)} readOnly="true"></input>
              </div>

            <div className="input-group mb-3">
              <div className="input-group-prepend">
                <span className="input-group-text">Holdings:</span>
              </div>
              <input type="text" className="form-control" value={this.props.btcHoldings} readOnly="true"></input>
            </div>
            <div className="input-group mb-3">
              <div className="input-group-prepend">
                <span className="input-group-text">Units to buy/sell</span>
              </div>
              <input type="text" className="form-control"  value={this.state.units} onChange={this.updateUnits}></input>
              <div className="input-group-append">
                <button className="btn btn-outline-success" type="button" onClick={this.buy} disabled={this.props.processing}>Buy</button>
              </div>
              <div className="input-group-append">
                <button className="btn btn-outline-danger" type="button" onClick={this.sell} disabled={this.props.processing}>Sell</button>
              </div>
            </div>
          </div>

          <div className="col-sm-4">
            <h3>Ethereum</h3>
            TODO
          </div>
      </div>
            </div>);
            }
}
