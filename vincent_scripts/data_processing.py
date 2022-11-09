"""
This script holds theㅌㅋ functions used for processing raw data/drawing the heatmaps
Ask Joonhwan for any clarifications
"""
import profile
import pandas as pd
import numpy as np
import argparse
import matplotlib.pyplot as plt
import matplotlib.transforms
import matplotlib.ticker as tkr
import matplotlib.font_manager as fm
import seaborn as sns;
import matplotlib.colors as colors

benchmarks=["sunflow","avrora","pmd","jython","antlr","bloat","fop","luindex"]
# benchmarks = ["luindex"]   
"""
This function takes in the experiment directory for the first stage of profiling  
outputs a settings file for each benchmark to be consumed in the next step of profiling
"""
def profiling_generate_settings(experiment_dir, iterations):
    # Benchmarks to process
   
    for bench in benchmarks:
        # Read raw data and add header
        df = pd.read_csv('{}/{}/counter_based_sampling_kenan.0.csv'.format(experiment_dir, bench), header=None)
        df.columns = ['iteration','MethodStartupTime','MethodName','tid','junk','n1','n2','Package','X']
        # Disregarding first 5 iterations
        df = df[df.iteration>4]
        # Total Package Energy    
        total_energy = df['Package'].sum()
        
        # Replace arbitrary $ signs on method names
        df["MethodName"]=df["MethodName"].str.replace("$$$$$",".",regex=False)
        df["MethodName"]=df["MethodName"].str.replace("$",".",regex=False)
            
        # Groupby the sum of Package Energy
        df = df.groupby(['MethodName'], as_index=False)['Package'].sum()
        
        # Divide the sum by the total_energy
        df['Package'] = df['Package'].div(total_energy).round(4)
        
        # Extract top 5 energy consuming methods
        df = df.nlargest(5,'Package')
        df.columns = ['MethodName', 'Package']
        # save top5 methods into a csv file under exp
        df.to_csv('{}/{}/top5.csv'.format(experiment_dir, bench))
        
        # generating the settings file per benchmark under experimend_dir     
        top_5_method_lst=df['MethodName'].to_list()
        settings_file=open('{}/settings/{}_settings'.format(experiment_dir, bench), 'w+')
        for method_name in top_5_method_lst:
            if bench=='sunflow' or bench=='luindex' or bench=='avrora':
                settings_file.write('{},{},{},{}\n'.format(bench, 'new', iterations, method_name))
            else:
                settings_file.write('{},{},{},{}\n'.format(bench, 'old', iterations, method_name))
        settings_file.close()

"""
This function
outputs three heatmaps in a subdirectory called heatmaps in the experiment folder
"""
def generate_heatmaps(exp_dir):
    cmap = colors.LinearSegmentedColormap.from_list("n",["#00FF00","#110000", "#D3D3D3"])
    # benchlist=["sunflow", "avrora","pmd","fop","antlr","luindex", "bloat", "jython"]
    heat_type=["etm_best", "time_best" ,"energy_best"]

    sns.set(font_scale=1)

    for htype in heat_type:
        i=0
        fig,axs = plt.subplots(4, 2,figsize=(16,16))
        axs=axs.flatten()
        for bench in benchmarks:
                energy_df=pd.read_csv("{}/ratios/{}_{}.csv".format(exp_dir,bench, htype))

                data = {'Method': np.repeat(range(1,6), 11),
                        'Frequency': list(range(1,12))*5,
                        'Value': sum([energy_df.iloc[int("{}".format(i)), 1:12].to_list() for i in range(0,5)], [])
                }

                heatdf=pd.DataFrame(data)
                heatdf['Value']=heatdf["Value"].round(decimals=2)
                heatdf=heatdf.pivot("Method", "Frequency", "Value")

                cbar_ticks = np.linspace(heatdf.min().min(), heatdf.max().max(), 7)
                np.append(cbar_ticks, 1.00)
                norm = matplotlib.colors.TwoSlopeNorm(vcenter=1)

                ax = sns.heatmap(heatdf,annot=True,ax=axs[i], cmap=cmap,norm=norm, fmt="1.2f", square=True, cbar_kws={"shrink": 0.73,"ticks": cbar_ticks, "format": '%.2f'})
                axs[i].set_title(bench, fontsize=25, pad=10)

                i=i+1
        fig.tight_layout()
        fig.savefig('%s/heatmaps/%s_heat.pdf'  %(exp_dir, htype))

def write_file(p,v):
    f = open(p, "w")
    f.truncate()
    f.write("%f" %(v))
    f.close()

def read_file(p):
    with open(p, "r") as myfile:
        data = float(myfile.readlines()[0].strip())
        return data

def calculate_baseline_min(exp_dir):
    # lst=["avrora","pmd","antlr","bloat","fop","luindex"];
    lst=benchmarks
    for bench in lst:
        min_t = 10000000000;
        min_e = 10000000000;
        min_edp = 10000000000
        bestef=-1
        besttf=-1
        bestedpf=-1
        for i in range(1,13):
            if(i==1):
                continue

            e=read_file("%s/%s_%d_kenan_energy" %(exp_dir,bench,i))
            t=read_file("%s/%s_%d_execution_time" %(exp_dir ,bench,i))
            edp=e*t
            if(e>0):
                if(e<min_e):
                    min_e=e
                    bestef=i

                if(t<min_t):
                    min_t=t;
                    besttf=i

                if(edp<min_edp):
                    min_edp=edp;
                    bestedpf=i
            else:
                print("%s freq %d needs a rerun" % (bench, i))

        write_file("%s/%s_best_t" %(exp_dir,bench),min_t)
        write_file("%s/%s_best_e" % (exp_dir,bench),min_e)
        write_file("%s/%s_best_edp" % (exp_dir,bench), min_edp)

        write_file("%s/%s_best_t_f" %(exp_dir,bench),besttf)
        write_file("%s/%s_best_e_f" % (exp_dir,bench),bestef)
        write_file("%s/%s_best_edp_f" % (exp_dir,bench), bestedpf)


def main():
    # argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--function", help="what function to execute", type=str, required=True)
    parser.add_argument("--experiment_dir", help="directory of experiment", type=str, required=True)
    parser.add_argument("--iterations", help="number of iterations per experiment", type=int, required=True)
    # parser.add_argument("--iterations", help="number of iterations per experiment", type=int, required=True)
    
    args =  parser.parse_args()
    
    if args.function == 'profiling_generate_settings':
        profiling_generate_settings(args.experiment_dir, args.iterations)
        print(args.experiment_dir) 
    elif args.function == 'generate_heatmaps':
        generate_heatmaps(args.experiment_dir)
    elif args.function == 'calculate_min':
        calculate_baseline_min(args.experiment_dir)
    
    
    
if __name__ == '__main__':
    main()