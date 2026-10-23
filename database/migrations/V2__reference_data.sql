INSERT INTO assets(symbol,company_name,exchange,currency) VALUES
 ('AAPL','Apple Inc.','NASDAQ','USD'),('MSFT','Microsoft Corporation','NASDAQ','USD'),
 ('NVDA','NVIDIA Corporation','NASDAQ','USD'),('AMZN','Amazon.com Inc.','NASDAQ','USD'),
 ('GOOGL','Alphabet Inc.','NASDAQ','USD'),('META','Meta Platforms Inc.','NASDAQ','USD'),
 ('TSLA','Tesla Inc.','NASDAQ','USD'),('AMD','Advanced Micro Devices Inc.','NASDAQ','USD'),
 ('JPM','JPMorgan Chase & Co.','NYSE','USD'),('NFLX','Netflix Inc.','NASDAQ','USD');
INSERT INTO watchlists(name) VALUES ('Core watchlist');
INSERT INTO watchlist_items(watchlist_id,asset_id) SELECT w.id,a.id FROM watchlists w CROSS JOIN assets a WHERE w.name='Core watchlist';
INSERT INTO strategies(name,strategy_type,configuration) VALUES
 ('Buy & hold','BUY_AND_HOLD','{}'),
 ('Moving average crossover','MA_CROSSOVER','{"fastPeriod":20,"slowPeriod":50}'),
 ('ML direction','ML_DIRECTION','{}');
