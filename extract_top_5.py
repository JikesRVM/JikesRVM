import pandas as pd

df=[pd.read_csv('sunflow/counter_based_sampling_kenan.{}.csv'.format(i),header=None) for i in range(1,12)]
df
pd.concat(df)
pd.concat(df).groupby(2)[[5, 7]]
pd.concat(df).groupby(2)[[5, 7]].sum()
df = pd. concat(df).groupby(2)[[5, 7]].sum()

df.sort_values(by=[7,5], ascending=False)

df.index.str
df.index.str.replace('$$$$$",