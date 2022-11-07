"""
This script holds theㅌㅋ functions used for processing raw data/drawing the heatmaps
Ask Joonhwan for any clarifications
"""
import profile
import pandas as pd
import argparse

"""
This function takes in the experiment directory for the first stage of profiling  
outputs a settings file for each benchmark to be consumed in the next step of profiling
"""
def profiling_generate_settings(experiment_dir, iterations):
    # Benchmarks to process
    benchmarks=["sunflow","avrora","pmd","jython","antlr","bloat","fop","luindex"]
   
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
def generate_heatmaps():
    pass

def main():
    # argparse
    parser = argparse.ArgumentParser()
    parser.add_argument("--function", help="what function to execute", type=str, required=True)
    parser.add_argument("--experiment_dir", help="directory of experiment", type=str, required=True)
    parser.add_argument("--iterations", help="number of iterations per experiment", type=int, required=True)
    
    args =  parser.parse_args()
    if args.function == 'profiling_generate_settings':
        profiling_generate_settings(args.experiment_dir)
        print(args.experiment_dir) 
    elif args.function == 'generate_heatmaps':
        pass
   


if __name__ == '__main__':
    main()