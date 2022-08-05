import pandas as pd

df = pd.read_csv('sunflow/counter_based_sampling_kenan.0.csv')
df
pd.concat(df)
pd.concat(df).groupby(2)[[5, 7]]
pd.concat(df).groupby(2)[[5, 7]].sum()
df = pd. concat(df).groupby(2)[[5, 7]].sum()

df.sort_values(by=[7,5], ascending=False)

df.index.str
df.index.str.replace('$$$$$",